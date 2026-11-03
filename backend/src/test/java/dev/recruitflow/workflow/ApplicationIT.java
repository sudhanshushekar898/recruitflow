package dev.recruitflow.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class ApplicationIT extends WorkflowTestBase {

    private JsonNode job;
    private JsonNode candidate;
    private UUID clientId;

    @BeforeEach
    void setUpJob() throws Exception {
        clientId = UUID.fromString(createClient("Northwind Fintech").get("id").asText());
        job = createOpenJob(recruiterA, clientId, "Senior Java Engineer");
        candidate = createCandidate("sam.one@example.com");
    }

    interface Builder {
        MockHttpServletRequestBuilder build(UUID id);
    }

    static Stream<Arguments> endpoints() {
        return Stream.of(
                Arguments.of("list", (Builder) id -> get("/api/v1/applications")),
                Arguments.of("create", (Builder) id -> post("/api/v1/applications").contentType(MediaType.APPLICATION_JSON).content("{}")),
                Arguments.of("get", (Builder) id -> get("/api/v1/applications/" + id)),
                Arguments.of("move", (Builder) id -> post("/api/v1/applications/" + id + "/status").contentType(MediaType.APPLICATION_JSON).content("{\"toStatus\":\"SCREENING\",\"version\":0}")),
                Arguments.of("history", (Builder) id -> get("/api/v1/applications/" + id + "/history")),
                Arguments.of("candidate applications", (Builder) id -> get("/api/v1/candidates/" + id + "/applications")));
    }

    @ParameterizedTest(name = "anonymous cannot {0}")
    @MethodSource("endpoints")
    void anonymousIsUnauthorized(String name, Builder builder) throws Exception {
        mvc.perform(builder.build(UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "interviewer cannot {0}")
    @MethodSource("endpoints")
    void interviewersAreForbidden(String name, Builder builder) throws Exception {
        mvc.perform(builder.build(UUID.randomUUID()).header("Authorization", interviewer)).andExpect(status().isForbidden());
    }

    // ---- applying

    @Test
    void applyingCreatesANewApplicationWithItsFirstHistoryEntry() throws Exception {
        JsonNode application = apply(recruiterB, candidate, job);

        assertThat(application.get("status").asText()).isEqualTo("NEW");
        assertThat(application.get("ownerId").asText()).isEqualTo(recruiterBId.toString());
        assertThat(application.get("candidateName").asText()).isEqualTo("Sam sam.one");
        assertThat(application.get("jobTitle").asText()).isEqualTo("Senior Java Engineer");
        assertThat(application.get("clientName").asText()).isEqualTo("Northwind Fintech");
        assertThat(application.get("allowedNext").toString()).contains("SCREENING", "SHORTLISTED", "REJECTED", "WITHDRAWN");

        JsonNode history = send(recruiterA, get("/api/v1/applications/" + application.get("id").asText() + "/history"), 200);
        assertThat(history.size()).isEqualTo(1);
        assertThat(history.get(0).has("fromStatus")).isFalse();
        assertThat(history.get(0).get("toStatus").asText()).isEqualTo("NEW");
        assertThat(history.get(0).get("changedByName").asText()).isEqualTo("Test User");
        assertThat(auditCount("APPLICATION_CREATED")).isEqualTo(1);
    }

    @Test
    void onlyOpenJobsAcceptApplications() throws Exception {
        JsonNode draft = created(recruiterA, "/api/v1/jobs", jobBody(clientId, "Draft job"));

        MvcResult result = postJson(recruiterA, "/api/v1/applications",
                Map.of("candidateId", candidate.get("id").asText(), "jobId", draft.get("id").asText()));

        assertStatus(result, 422);
        assertThat(body(result).get("code").asText()).isEqualTo("JOB_NOT_OPEN");
    }

    @Test
    void archivedCandidatesCannotBeAdded() throws Exception {
        send(recruiterA, post("/api/v1/candidates/" + candidate.get("id").asText() + "/archive"), 200);

        MvcResult result = postJson(recruiterA, "/api/v1/applications",
                Map.of("candidateId", candidate.get("id").asText(), "jobId", job.get("id").asText()));

        assertStatus(result, 422);
        assertThat(body(result).get("code").asText()).isEqualTo("INVALID_OPERATION");
    }

    @Test
    void unknownCandidateOrJobIsAFieldError() throws Exception {
        MvcResult noCandidate = postJson(recruiterA, "/api/v1/applications", Map.of("candidateId", UUID.randomUUID(), "jobId", job.get("id").asText()));
        MvcResult noJob = postJson(recruiterA, "/api/v1/applications", Map.of("candidateId", candidate.get("id").asText(), "jobId", UUID.randomUUID()));

        assertStatus(noCandidate, 400);
        assertThat(body(noCandidate).at("/errors/0/field").asText()).isEqualTo("candidateId");
        assertStatus(noJob, 400);
        assertThat(body(noJob).at("/errors/0/field").asText()).isEqualTo("jobId");
    }

    @Test
    void theSameCandidateCannotApplyTwiceAndTheErrorPointsToTheFirstApplication() throws Exception {
        JsonNode first = apply(recruiterA, candidate, job);

        MvcResult second = postJson(recruiterB, "/api/v1/applications",
                Map.of("candidateId", candidate.get("id").asText(), "jobId", job.get("id").asText()));

        assertStatus(second, 409);
        assertThat(body(second).get("code").asText()).isEqualTo("DUPLICATE_APPLICATION");
        assertThat(body(second).get("existingApplicationId").asText()).isEqualTo(first.get("id").asText());
    }

    @Test
    void concurrentApplicationsOfTheSamePairYieldExactlyOneRow() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                Callable<Integer> task = () -> postJson(recruiterA, "/api/v1/applications",
                        Map.of("candidateId", candidate.get("id").asText(), "jobId", job.get("id").asText())).getResponse().getStatus();
                results.add(pool.submit(task));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) {
                statuses.add(f.get());
            }
            assertThat(statuses.stream().filter(s -> s == 201)).hasSize(1);
            assertThat(statuses.stream().filter(s -> s == 409)).hasSize(5);
            assertThat(jdbc.queryForObject("select count(*) from applications", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from application_status_history", Integer.class)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- moving through the pipeline

    @Test
    void anApplicationCanWalkTheWholePipelineAndEveryStepIsRecorded() throws Exception {
        JsonNode app = apply(recruiterA, candidate, job);
        for (String next : List.of("SCREENING", "SHORTLISTED", "INTERVIEW", "OFFER", "HIRED")) {
            app = moved(recruiterA, app, next);
            assertThat(app.get("status").asText()).isEqualTo(next);
        }
        assertThat(app.get("allowedNext").size()).isZero();

        JsonNode history = send(recruiterA, get("/api/v1/applications/" + app.get("id").asText() + "/history"), 200);
        List<String> path = new ArrayList<>();
        history.forEach(h -> path.add(h.get("toStatus").asText()));
        assertThat(path).containsExactly("NEW", "SCREENING", "SHORTLISTED", "INTERVIEW", "OFFER", "HIRED");
        assertThat(history.get(1).get("fromStatus").asText()).isEqualTo("NEW");
        assertThat(auditCount("APPLICATION_STATUS_CHANGED")).isEqualTo(5);
    }

    @Test
    void skippingStagesBackwardsMovesAndMovesFromTerminalStatesAreRejectedWithTheAllowedList() throws Exception {
        JsonNode app = apply(recruiterA, candidate, job);

        MvcResult skip = move(recruiterA, app, "HIRED");
        assertStatus(skip, 422);
        assertThat(body(skip).get("code").asText()).isEqualTo("INVALID_TRANSITION");
        assertThat(body(skip).get("allowed").toString()).contains("SCREENING").doesNotContain("HIRED");

        app = moved(recruiterA, app, "SHORTLISTED");
        assertStatus(move(recruiterA, app, "NEW"), 422);
        app = moved(recruiterA, app, "WITHDRAWN");
        assertStatus(move(recruiterA, app, "NEW"), 422);
        assertThat(jdbc.queryForObject("select status from applications", String.class)).isEqualTo("WITHDRAWN");
    }

    @Test
    void aStaleBoardIsRefusedInsteadOfOverwritingSomeoneElsesMove() throws Exception {
        JsonNode app = apply(recruiterA, candidate, job);
        moved(recruiterB, app, "SCREENING"); // someone else moves it first

        MvcResult stale = move(recruiterA, app, "SHORTLISTED"); // using the version from before

        assertStatus(stale, 409);
        assertThat(body(stale).get("code").asText()).isEqualTo("STALE_VERSION");
    }

    @Test
    void rejectingNeedsAReasonWhichIsStoredAndClearedWhenReopened() throws Exception {
        JsonNode app = apply(recruiterA, candidate, job);

        MvcResult noReason = move(recruiterA, app, "REJECTED");
        assertStatus(noReason, 400);
        assertThat(body(noReason).at("/errors/0/field").asText()).isEqualTo("reason");

        MvcResult rejected = postJson(recruiterA, "/api/v1/applications/" + app.get("id").asText() + "/status",
                Map.of("toStatus", "REJECTED", "reason", "SKILLS_MISMATCH", "comment", "No production Java", "version", app.get("version").asLong()));
        assertStatus(rejected, 200);
        JsonNode r = body(rejected).get("application");
        assertThat(r.get("rejectionReason").asText()).isEqualTo("SKILLS_MISMATCH");
        assertThat(r.get("allowedNext").toString()).contains("NEW");

        JsonNode reopened = moved(recruiterA, r, "NEW");
        assertThat(reopened.has("rejectionReason")).isFalse();
        JsonNode history = send(recruiterA, get("/api/v1/applications/" + app.get("id").asText() + "/history"), 200);
        assertThat(history.get(1).get("comment").asText()).isEqualTo("No production Java");
    }

    @Test
    void offersHiresAndReopeningAreReservedForTheOwnerOrAnAdmin() throws Exception {
        JsonNode app = apply(recruiterA, candidate, job);
        // another recruiter may work the early stages...
        app = moved(recruiterB, app, "SCREENING");
        app = moved(recruiterB, app, "SHORTLISTED");
        app = moved(recruiterB, app, "INTERVIEW");
        // ...but does not even see the restricted moves, and is refused if they try
        assertThat(app.get("allowedNext").toString()).contains("REJECTED").doesNotContain("OFFER");
        MvcResult forbidden = move(recruiterB, app, "OFFER");
        assertStatus(forbidden, 403);
        assertThat(body(forbidden).get("code").asText()).isEqualTo("FORBIDDEN");

        // the owner sees and can make the move
        JsonNode asOwner = send(recruiterA, get("/api/v1/applications/" + app.get("id").asText()), 200);
        assertThat(asOwner.get("allowedNext").toString()).contains("OFFER");
        app = moved(recruiterA, asOwner, "OFFER");
        assertStatus(move(recruiterB, app, "HIRED"), 403);
        // an admin can too
        app = moved(admin, app, "HIRED");
        assertThat(app.get("status").asText()).isEqualTo("HIRED");

        // reopening a rejection is restricted the same way
        JsonNode other = createCandidate("sam.two@example.com");
        JsonNode second = apply(recruiterA, other, job);
        JsonNode rejected = body(postJson(recruiterB, "/api/v1/applications/" + second.get("id").asText() + "/status",
                Map.of("toStatus", "REJECTED", "reason", "OTHER", "version", second.get("version").asLong()))).get("application");
        assertStatus(move(recruiterB, rejected, "NEW"), 403);
        assertThat(moved(recruiterA, rejected, "NEW").get("status").asText()).isEqualTo("NEW");
    }

    @Test
    void hiringTheLastOpeningSuggestsClosingTheJob() throws Exception {
        JsonNode app = apply(recruiterA, candidate, job);
        for (String next : List.of("SCREENING", "SHORTLISTED", "INTERVIEW", "OFFER")) {
            app = moved(recruiterA, app, next);
        }

        MvcResult hired = move(recruiterA, app, "HIRED");

        assertStatus(hired, 200);
        assertThat(body(hired).get("jobCloseSuggested").asBoolean()).isTrue();
    }

    @Test
    void hiringDoesNotSuggestClosingWhileOpeningsRemain() throws Exception {
        JsonNode twoOpenings = createOpenJob(recruiterA, clientId, "Two seats");
        send(recruiterA, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/jobs/" + twoOpenings.get("id").asText())
                .contentType(MediaType.APPLICATION_JSON).content("{\"openings\":2,\"version\":" + twoOpenings.get("version").asLong() + "}"), 200);
        JsonNode app = apply(recruiterA, candidate, twoOpenings);
        for (String next : List.of("SCREENING", "SHORTLISTED", "INTERVIEW", "OFFER")) {
            app = moved(recruiterA, app, next);
        }

        MvcResult hired = move(recruiterA, app, "HIRED");

        assertThat(body(hired).get("jobCloseSuggested").asBoolean()).isFalse();
    }

    // ---- reading

    @Test
    void theBoardGroupsApplicationsByStageInPipelineOrder() throws Exception {
        JsonNode a = apply(recruiterA, candidate, job);
        JsonNode b = apply(recruiterA, createCandidate("sam.two@example.com"), job);
        JsonNode c = apply(recruiterA, createCandidate("sam.three@example.com"), job);
        moved(recruiterA, a, "SCREENING");
        JsonNode shortlisted = moved(recruiterA, b, "SHORTLISTED");
        moved(recruiterA, shortlisted, "REJECTED".equals("x") ? "NEW" : "WITHDRAWN");

        JsonNode board = send(recruiterB, get("/api/v1/jobs/" + job.get("id").asText() + "/pipeline"), 200);

        assertThat(board.get("jobTitle").asText()).isEqualTo("Senior Java Engineer");
        assertThat(board.get("clientName").asText()).isEqualTo("Northwind Fintech");
        assertThat(board.get("openings").asInt()).isEqualTo(1);
        List<String> order = new ArrayList<>();
        board.get("stages").forEach(s -> order.add(s.get("status").asText()));
        assertThat(order).containsExactly("NEW", "SCREENING", "SHORTLISTED", "INTERVIEW", "OFFER", "HIRED", "REJECTED", "WITHDRAWN");
        assertThat(stage(board, "NEW").get("count").asInt()).isEqualTo(1);
        assertThat(stage(board, "NEW").at("/applications/0/id").asText()).isEqualTo(c.get("id").asText());
        assertThat(stage(board, "SCREENING").get("count").asInt()).isEqualTo(1);
        assertThat(stage(board, "WITHDRAWN").get("count").asInt()).isEqualTo(1);
        assertThat(stage(board, "SHORTLISTED").get("count").asInt()).isZero();
    }

    private JsonNode stage(JsonNode board, String status) {
        for (JsonNode s : board.get("stages")) {
            if (s.get("status").asText().equals(status)) {
                return s;
            }
        }
        throw new AssertionError("no stage " + status);
    }

    @Test
    void unknownJobHasNoBoard() throws Exception {
        mvc.perform(get("/api/v1/jobs/" + UUID.randomUUID() + "/pipeline").header("Authorization", recruiterA))
                .andExpect(status().isNotFound());
    }

    @Test
    void aCandidatesApplicationsAreListedNewestFirstWithJobDetails() throws Exception {
        JsonNode second = createOpenJob(recruiterA, clientId, "Platform Engineer");
        apply(recruiterA, candidate, job);
        apply(recruiterA, candidate, second);

        mvc.perform(get("/api/v1/candidates/" + candidate.get("id").asText() + "/applications").header("Authorization", recruiterB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].jobTitle").value("Platform Engineer"))
                .andExpect(jsonPath("$[1].jobTitle").value("Senior Java Engineer"));
        mvc.perform(get("/api/v1/candidates/" + UUID.randomUUID() + "/applications").header("Authorization", recruiterA))
                .andExpect(status().isNotFound());
    }

    @Test
    void theListFiltersByJobStatusAndOwnerAndPages() throws Exception {
        JsonNode a = apply(recruiterA, candidate, job);
        apply(recruiterB, createCandidate("sam.two@example.com"), job);
        moved(recruiterA, a, "SCREENING");

        mvc.perform(get("/api/v1/applications?jobId=" + job.get("id").asText()).header("Authorization", recruiterA))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/applications?status=SCREENING").header("Authorization", recruiterA))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/applications?ownerId=" + recruiterBId).header("Authorization", recruiterA))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/applications?size=1&page=1&sort=appliedAt,asc").header("Authorization", recruiterA))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/v1/applications?sort=ownerId").header("Authorization", recruiterA)).andExpect(status().isBadRequest());
    }

    @Test
    void theDatabaseItselfRefusesARejectionWithoutAReasonAndADuplicatePair() {
        JsonNode app;
        try {
            app = apply(recruiterA, candidate, job);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        String id = app.get("id").asText();

        assertThatThrownBy(() -> jdbc.update("update applications set status = 'REJECTED' where id = ?::uuid", id))
                .hasMessageContaining("ck_applications_rejection_reason");
        assertThatThrownBy(() -> jdbc.update("""
                insert into applications (id, candidate_id, job_id, status, source, owner_id, applied_at, status_changed_at)
                select gen_random_uuid(), candidate_id, job_id, 'NEW', 'OTHER', owner_id, now(), now() from applications"""))
                .hasMessageContaining("uq_applications_candidate_job");
    }
}
