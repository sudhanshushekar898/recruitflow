package dev.recruitflow.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class InterviewIT extends WorkflowTestBase {

    private static final Instant TOMORROW_10 = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

    private JsonNode job;
    private JsonNode shortlisted;

    @BeforeEach
    void shortlistedApplication() throws Exception {
        UUID clientId = UUID.fromString(createClient("Northwind Fintech").get("id").asText());
        job = createOpenJob(recruiterA, clientId, "Senior Java Engineer");
        shortlisted = moved(recruiterA, apply(recruiterA, createCandidate("sam.one@example.com"), job), "SHORTLISTED");
    }

    private Map<String, Object> body(JsonNode application, Instant start, UUID... panel) {
        List<Map<String, Object>> participants = new ArrayList<>();
        for (int i = 0; i < panel.length; i++) {
            participants.add(Map.of("userId", panel[i], "role", i == 0 ? "LEAD" : "PANEL"));
        }
        return Map.of("applicationId", application.get("id").asText(), "type", "VIDEO", "start", start.toString(),
                "end", start.plus(1, ChronoUnit.HOURS).toString(), "locationOrLink", "https://meet.example.com/abc",
                "participants", participants);
    }

    private JsonNode schedule(JsonNode application, Instant start, UUID... panel) throws Exception {
        return created(recruiterA, "/api/v1/interviews", body(application, start, panel));
    }

    private JsonNode anotherShortlisted(String email) throws Exception {
        return moved(recruiterA, apply(recruiterA, createCandidate(email), job), "SHORTLISTED");
    }

    // ---- scheduling

    @Test
    void schedulingMovesAShortlistedApplicationToInterviewAndRecordsWhy() throws Exception {
        JsonNode interview = schedule(shortlisted, TOMORROW_10, interviewerId, interviewer2Id);

        assertThat(interview.get("roundNo").asInt()).isEqualTo(1);
        assertThat(interview.get("status").asText()).isEqualTo("SCHEDULED");
        assertThat(interview.get("candidateName").asText()).isEqualTo("Sam sam.one");
        assertThat(interview.get("jobTitle").asText()).isEqualTo("Senior Java Engineer");
        assertThat(interview.get("participants").size()).isEqualTo(2);
        assertThat(interview.at("/participants/0/role").asText()).isEqualTo("LEAD");
        assertThat(interview.get("applicationStatus").asText()).isEqualTo("INTERVIEW");

        JsonNode history = send(recruiterA, get("/api/v1/applications/" + shortlisted.get("id").asText() + "/history"), 200);
        JsonNode last = history.get(history.size() - 1);
        assertThat(last.get("toStatus").asText()).isEqualTo("INTERVIEW");
        assertThat(last.get("comment").asText()).isEqualTo("Interview scheduled");
        assertThat(auditCount("INTERVIEW_SCHEDULED")).isEqualTo(1);
    }

    @Test
    void secondRoundsAreNumberedAndDoNotMoveTheApplicationAgain() throws Exception {
        schedule(shortlisted, TOMORROW_10, interviewerId);

        JsonNode second = schedule(shortlisted, TOMORROW_10.plus(1, ChronoUnit.DAYS), interviewerId);

        assertThat(second.get("roundNo").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from application_status_history where to_status = 'INTERVIEW'", Integer.class)).isEqualTo(1);
    }

    @Test
    void onlyShortlistedOrInterviewStageApplicationsCanBeScheduled() throws Exception {
        JsonNode fresh = apply(recruiterA, createCandidate("sam.two@example.com"), job); // still NEW

        MvcResult result = postJson(recruiterA, "/api/v1/interviews", body(fresh, TOMORROW_10, interviewerId));

        assertStatus(result, 422);
        assertThat(body(result).get("code").asText()).isEqualTo("INVALID_OPERATION");
    }

    @Test
    void theTimeWindowAndPanelAreValidated() throws Exception {
        Map<String, Object> past = body(shortlisted, Instant.now().minus(1, ChronoUnit.HOURS), interviewerId);
        Map<String, Object> backwards = new java.util.HashMap<>(body(shortlisted, TOMORROW_10, interviewerId));
        backwards.put("end", TOMORROW_10.minus(1, ChronoUnit.HOURS).toString());
        Map<String, Object> stranger = body(shortlisted, TOMORROW_10, UUID.randomUUID());
        Map<String, Object> twice = body(shortlisted, TOMORROW_10, interviewerId, interviewerId);

        for (Map.Entry<Map<String, Object>, String> c : Map.of(past, "start", backwards, "end", stranger, "participants",
                twice, "participants").entrySet()) {
            MvcResult result = postJson(recruiterA, "/api/v1/interviews", c.getKey());
            assertStatus(result, 400);
            assertThat(body(result).at("/errors/0/field").asText()).isEqualTo(c.getValue());
        }
        Map<String, Object> nobody = new java.util.HashMap<>(body(shortlisted, TOMORROW_10, interviewerId));
        nobody.put("participants", List.of());
        assertStatus(postJson(recruiterA, "/api/v1/interviews", nobody), 400);
    }

    // ---- clash detection

    @Test
    void anInterviewerCannotBeDoubleBookedButBackToBackAndOtherPeopleAreFine() throws Exception {
        JsonNode first = schedule(shortlisted, TOMORROW_10, interviewerId);
        JsonNode other = anotherShortlisted("sam.two@example.com");

        MvcResult clash = postJson(recruiterA, "/api/v1/interviews", body(other, TOMORROW_10.plus(30, ChronoUnit.MINUTES), interviewerId));
        assertStatus(clash, 409);
        assertThat(body(clash).get("code").asText()).isEqualTo("INTERVIEW_CONFLICT");
        assertThat(body(clash).at("/conflicts/0/interviewId").asText()).isEqualTo(first.get("id").asText());
        assertThat(body(clash).at("/conflicts/0/userName").asText()).isEqualTo("Test User");

        // starting exactly when the first ends is not a clash, and a different interviewer is free
        schedule(other, TOMORROW_10.plus(1, ChronoUnit.HOURS), interviewerId);
        schedule(anotherShortlisted("sam.three@example.com"), TOMORROW_10.plus(15, ChronoUnit.MINUTES), interviewer2Id);
    }

    @Test
    void aCancelledInterviewFreesTheSlot() throws Exception {
        JsonNode first = schedule(shortlisted, TOMORROW_10, interviewerId);
        send(recruiterA, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/interviews/" + first.get("id").asText() + "/cancel"), 200);

        schedule(anotherShortlisted("sam.two@example.com"), TOMORROW_10, interviewerId);
    }

    @Test
    void simultaneousBookingsOfTheSameInterviewerCannotBothSucceed() throws Exception {
        List<JsonNode> applications = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            applications.add(anotherShortlisted("race" + i + "@example.com"));
        }
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (JsonNode application : applications) {
                Callable<Integer> task = () -> postJson(recruiterA, "/api/v1/interviews", body(application, TOMORROW_10, interviewerId))
                        .getResponse().getStatus();
                results.add(pool.submit(task));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) {
                statuses.add(f.get());
            }
            assertThat(statuses.stream().filter(s -> s == 201)).hasSize(1);
            assertThat(statuses.stream().filter(s -> s == 409)).hasSize(4);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void reschedulingChecksClashesButNotAgainstItselfAndNeedsTheCurrentVersion() throws Exception {
        JsonNode first = schedule(shortlisted, TOMORROW_10, interviewerId);
        schedule(anotherShortlisted("sam.two@example.com"), TOMORROW_10.plus(2, ChronoUnit.HOURS), interviewerId);
        String path = "/api/v1/interviews/" + first.get("id").asText();

        // moving 30 minutes later only overlaps its own old slot: allowed
        mvc.perform(patch(path).header("Authorization", recruiterA).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("start", TOMORROW_10.plus(30, ChronoUnit.MINUTES).toString(),
                                "end", TOMORROW_10.plus(90, ChronoUnit.MINUTES).toString(), "version", first.get("version").asLong()))))
                .andExpect(status().isOk());
        // moving onto the other interview is a clash
        mvc.perform(patch(path).header("Authorization", recruiterA).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("start", TOMORROW_10.plus(2, ChronoUnit.HOURS).toString(),
                                "end", TOMORROW_10.plus(3, ChronoUnit.HOURS).toString(), "version", first.get("version").asLong() + 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INTERVIEW_CONFLICT"));
        // a stale version is refused
        mvc.perform(patch(path).header("Authorization", recruiterA).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("locationOrLink", "Room 4", "version", first.get("version").asLong()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    @Test
    void onlyScheduledInterviewsCanBeCancelledOrCompleted() throws Exception {
        JsonNode interview = schedule(shortlisted, TOMORROW_10, interviewerId);
        String path = "/api/v1/interviews/" + interview.get("id").asText();

        assertThat(send(recruiterA, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path + "/complete"), 200)
                .get("status").asText()).isEqualTo("COMPLETED");
        send(recruiterA, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path + "/cancel"), 422);
    }

    // ---- who can see what

    @Test
    void interviewersSeeOnlyTheirOwnInterviewsAndNoCandidateIdentifiers() throws Exception {
        JsonNode mine = schedule(shortlisted, TOMORROW_10, interviewerId);
        JsonNode notMine = schedule(anotherShortlisted("sam.two@example.com"), TOMORROW_10, interviewer2Id);

        JsonNode list = send(interviewer, get("/api/v1/interviews/mine?scope=upcoming"), 200);
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).get("id").asText()).isEqualTo(mine.get("id").asText());
        assertThat(list.get(0).has("candidateId")).isFalse();
        assertThat(list.get(0).get("canManage").asBoolean()).isFalse();
        assertThat(list.get(0).get("canGiveFeedback").asBoolean()).isTrue();

        send(interviewer, get("/api/v1/interviews/" + notMine.get("id").asText()), 403);
        send(interviewer, get("/api/v1/interviews"), 403);
        send(interviewer, get("/api/v1/interviews/interviewers"), 403);
        assertStatus(postJson(interviewer, "/api/v1/interviews", body(shortlisted, TOMORROW_10, interviewerId)), 403);
        // a recruiter sees everything, including where to link to
        assertThat(send(recruiterB, get("/api/v1/interviews/" + notMine.get("id").asText()), 200).has("candidateId")).isTrue();
    }

    @Test
    void recruitersCanListByInterviewerAndPickFromTheInterviewerDirectory() throws Exception {
        schedule(shortlisted, TOMORROW_10, interviewerId);
        schedule(anotherShortlisted("sam.two@example.com"), TOMORROW_10, interviewer2Id);

        mvc.perform(get("/api/v1/interviews?interviewerId=" + interviewer2Id).header("Authorization", recruiterB))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/interviews?status=SCHEDULED").header("Authorization", recruiterB))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/interviews/interviewers").header("Authorization", recruiterA))
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ---- feedback

    @Test
    void panelMembersGiveFeedbackOnceAndOutsidersCannot() throws Exception {
        JsonNode interview = schedule(shortlisted, TOMORROW_10, interviewerId);
        String path = "/api/v1/interviews/" + interview.get("id").asText() + "/feedback";
        Map<String, Object> feedback = Map.of("rating", 4, "recommendation", "YES", "strengths", "Clear thinker", "concerns", "Notice period");

        MvcResult first = postJson(interviewer, path, feedback);
        assertStatus(first, 201);
        assertThat(body(first).get("mine").asBoolean()).isTrue();
        assertStatus(postJson(interviewer, path, feedback), 409);
        assertStatus(postJson(interviewer2, path, feedback), 403);
        assertStatus(postJson(interviewer, path, Map.of("rating", 9, "recommendation", "YES")), 400);
        assertThat(send(interviewer, get("/api/v1/interviews/" + interview.get("id").asText()), 200)
                .get("canGiveFeedback").asBoolean()).isFalse();
        assertThat(auditCount("FEEDBACK_SUBMITTED")).isEqualTo(1);
    }

    @Test
    void feedbackIsBlindUntilYouHaveGivenYourOwn() throws Exception {
        JsonNode interview = schedule(shortlisted, TOMORROW_10, interviewerId, interviewer2Id);
        String path = "/api/v1/interviews/" + interview.get("id").asText() + "/feedback";
        assertStatus(postJson(interviewer, path, Map.of("rating", 5, "recommendation", "STRONG_YES")), 201);

        JsonNode before = send(interviewer2, get(path), 200);
        assertThat(before.get("blinded").asBoolean()).isTrue();
        assertThat(before.get("hiddenCount").asInt()).isEqualTo(1);
        assertThat(before.get("feedback").size()).isZero();

        assertStatus(postJson(interviewer2, path, Map.of("rating", 2, "recommendation", "NO")), 201);
        JsonNode after = send(interviewer2, get(path), 200);
        assertThat(after.get("blinded").asBoolean()).isFalse();
        assertThat(after.get("feedback").size()).isEqualTo(2);

        // recruiters always see everything
        assertThat(send(recruiterB, get(path), 200).get("feedback").size()).isEqualTo(2);
    }

    @Test
    void cancelledInterviewsTakeNoFeedback() throws Exception {
        JsonNode interview = schedule(shortlisted, TOMORROW_10, interviewerId);
        send(recruiterA, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/interviews/" + interview.get("id").asText() + "/cancel"), 200);

        assertStatus(postJson(interviewer, "/api/v1/interviews/" + interview.get("id").asText() + "/feedback",
                Map.of("rating", 3, "recommendation", "NO")), 422);
    }
}
