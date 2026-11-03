package dev.recruitflow.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class JobIT extends WorkflowTestBase {

    interface Builder {
        MockHttpServletRequestBuilder build(UUID id);
    }

    static Stream<Arguments> endpoints() {
        return Stream.of(
                Arguments.of("list clients", (Builder) id -> get("/api/v1/clients")),
                Arguments.of("create client", (Builder) id -> post("/api/v1/clients").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}")),
                Arguments.of("list jobs", (Builder) id -> get("/api/v1/jobs")),
                Arguments.of("get job", (Builder) id -> get("/api/v1/jobs/" + id)),
                Arguments.of("create job", (Builder) id -> post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content("{}")),
                Arguments.of("job status", (Builder) id -> post("/api/v1/jobs/" + id + "/status").contentType(MediaType.APPLICATION_JSON).content("{\"toStatus\":\"OPEN\"}")),
                Arguments.of("job skills", (Builder) id -> put("/api/v1/jobs/" + id + "/skills").contentType(MediaType.APPLICATION_JSON).content("{\"skills\":[]}")),
                Arguments.of("pipeline", (Builder) id -> get("/api/v1/jobs/" + id + "/pipeline")));
    }

    @ParameterizedTest(name = "anonymous cannot {0}")
    @MethodSource("endpoints")
    void anonymousIsUnauthorized(String name, Builder builder) throws Exception {
        mvc.perform(builder.build(UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "interviewer cannot {0}")
    @MethodSource("endpoints")
    void interviewersAreForbidden(String name, Builder builder) throws Exception {
        mvc.perform(builder.build(UUID.randomUUID()).header("Authorization", interviewer))
                .andExpect(status().isForbidden());
    }

    // ---- clients

    @Test
    void clientsAreCreatedUniquelyAndSearchable() throws Exception {
        JsonNode created = createClient("Northwind Fintech");
        assertThat(created.get("active").asBoolean()).isTrue();

        MvcResult duplicate = postJson(recruiterB, "/api/v1/clients", Map.of("name", " northwind fintech"));
        assertStatus(duplicate, 409);
        assertThat(body(duplicate).get("code").asText()).isEqualTo("DUPLICATE_CLIENT");

        createClient("Atlas Logistics");
        mvc.perform(get("/api/v1/clients?q=ATLAS").header("Authorization", recruiterA))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Atlas Logistics"));
        mvc.perform(get("/api/v1/clients?sort=name,asc").header("Authorization", recruiterA))
                .andExpect(jsonPath("$.content[0].name").value("Atlas Logistics"));
    }

    @Test
    void clientUpdatesNeedTheCurrentVersionAndCanDeactivate() throws Exception {
        JsonNode client = createClient("Helix Retail");
        String path = "/api/v1/clients/" + client.get("id").asText();

        mvc.perform(patch(path).header("Authorization", recruiterB).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"industry\":\"Retail\",\"active\":false,\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.industry").value("Retail"))
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(patch(path).header("Authorization", recruiterB).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"industry\":\"Stale\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
        mvc.perform(get("/api/v1/clients?active=true").header("Authorization", recruiterA))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void clientBodyIsValidated() throws Exception {
        MvcResult result = postJson(recruiterA, "/api/v1/clients", Map.of("name", "", "contactEmail", "nope"));

        assertStatus(result, 400);
        assertThat(body(result).get("errors").size()).isEqualTo(2);
    }

    // ---- jobs

    @Test
    void createdJobStartsAsADraftOwnedByItsCreator() throws Exception {
        JsonNode client = createClient("Northwind Fintech");

        JsonNode job = created(recruiterA, "/api/v1/jobs", jobBody(UUID.fromString(client.get("id").asText()), "Senior Java Engineer"));

        assertThat(job.get("status").asText()).isEqualTo("DRAFT");
        assertThat(job.get("ownerId").asText()).isEqualTo(recruiterAId.toString());
        assertThat(job.get("clientName").asText()).isEqualTo("Northwind Fintech");
        assertThat(job.get("openings").asInt()).isEqualTo(1);
        assertThat(job.at("/skills/0/displayName").asText()).isEqualTo("Java");
        assertThat(job.at("/skills/0/importance").asText()).isEqualTo("REQUIRED");
        assertThat(job.get("canEdit").asBoolean()).isTrue();
        List<String> allowed = new java.util.ArrayList<>();
        job.get("allowedStatuses").forEach(s -> allowed.add(s.asText()));
        assertThat(allowed).containsExactlyInAnyOrder("OPEN", "CLOSED");
        assertThat(auditCount("JOB_CREATED")).isEqualTo(1);
    }

    @Test
    void jobValidationCoversClientSalarySkillsAndFields() throws Exception {
        JsonNode client = createClient("Northwind Fintech");
        UUID clientId = UUID.fromString(client.get("id").asText());

        Map<String, Object> noClient = jobBody(UUID.randomUUID(), "Engineer");
        Map<String, Object> inverted = jobBody(clientId, "Engineer");
        inverted.put("salaryMin", 90000);
        inverted.put("salaryMax", 50000);
        Map<String, Object> noCurrency = jobBody(clientId, "Engineer");
        noCurrency.remove("currency");
        Map<String, Object> badSkill = jobBody(clientId, "Engineer");
        badSkill.put("skills", List.of(Map.of("skillId", UUID.randomUUID(), "importance", "REQUIRED")));
        Map<String, Object> blank = jobBody(clientId, "");
        blank.put("openings", 0);

        for (Map.Entry<Map<String, Object>, String> c : Map.of(noClient, "clientId", inverted, "salaryMax",
                noCurrency, "currency", badSkill, "skills").entrySet()) {
            MvcResult result = postJson(recruiterA, "/api/v1/jobs", c.getKey());
            assertStatus(result, 400);
            assertThat(body(result).at("/errors/0/field").asText()).isEqualTo(c.getValue());
        }
        MvcResult result = postJson(recruiterA, "/api/v1/jobs", blank);
        assertStatus(result, 400);
        assertThat(body(result).get("errors").size()).isEqualTo(2);
    }

    @Test
    void inactiveClientsCannotReceiveNewJobs() throws Exception {
        JsonNode client = createClient("Dormant Co");
        mvc.perform(patch("/api/v1/clients/" + client.get("id").asText()).header("Authorization", recruiterA)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false,\"version\":0}")).andExpect(status().isOk());

        MvcResult result = postJson(recruiterA, "/api/v1/jobs", jobBody(UUID.fromString(client.get("id").asText()), "Engineer"));

        assertStatus(result, 400);
        assertThat(body(result).at("/errors/0/message").asText()).contains("inactive");
    }

    @Test
    void lifecycleFollowsTheRulesAndTracksOpenedAndClosedDates() throws Exception {
        JsonNode client = createClient("Northwind Fintech");
        JsonNode job = created(recruiterA, "/api/v1/jobs", jobBody(UUID.fromString(client.get("id").asText()), "Engineer"));
        String path = "/api/v1/jobs/" + job.get("id").asText() + "/status";

        MvcResult illegal = postJson(recruiterA, path, Map.of("toStatus", "ON_HOLD"));
        assertStatus(illegal, 422);
        assertThat(body(illegal).get("code").asText()).isEqualTo("INVALID_TRANSITION");
        assertThat(body(illegal).get("allowed").toString()).contains("OPEN", "CLOSED");

        JsonNode open = body(postJson(recruiterA, path, Map.of("toStatus", "OPEN")));
        assertThat(open.get("openedAt").isNull()).isFalse();
        String firstOpened = open.get("openedAt").asText();
        assertThat(body(postJson(recruiterA, path, Map.of("toStatus", "ON_HOLD"))).get("status").asText()).isEqualTo("ON_HOLD");
        assertThat(body(postJson(recruiterA, path, Map.of("toStatus", "OPEN"))).get("openedAt").asText()).isEqualTo(firstOpened);
        JsonNode closed = body(postJson(recruiterA, path, Map.of("toStatus", "CLOSED")));
        assertThat(closed.get("closedAt").isNull()).isFalse();
        JsonNode reopened = body(postJson(recruiterA, path, Map.of("toStatus", "OPEN")));
        assertThat(reopened.get("status").asText()).isEqualTo("OPEN");
        assertThat(reopened.has("closedAt")).isFalse();
        assertThat(auditCount("JOB_STATUS_CHANGED")).isEqualTo(5);
    }

    @Test
    void onlyTheOwnerOrAnAdminCanChangeAJobButEveryoneCanRead() throws Exception {
        JsonNode client = createClient("Northwind Fintech");
        JsonNode job = created(recruiterA, "/api/v1/jobs", jobBody(UUID.fromString(client.get("id").asText()), "Engineer"));
        String id = job.get("id").asText();

        // another recruiter can read it, sees canEdit=false, and is refused every change
        mvc.perform(get("/api/v1/jobs/" + id).header("Authorization", recruiterB))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canEdit").value(false));
        mvc.perform(patch("/api/v1/jobs/" + id).header("Authorization", recruiterB).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Hijacked\",\"version\":0}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/jobs/" + id + "/status").header("Authorization", recruiterB).contentType(MediaType.APPLICATION_JSON)
                .content("{\"toStatus\":\"OPEN\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/jobs/" + id + "/skills").header("Authorization", recruiterB).contentType(MediaType.APPLICATION_JSON)
                .content("{\"skills\":[]}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/jobs?q=engineer").header("Authorization", recruiterB))
                .andExpect(jsonPath("$.content[0].canEdit").value(false));

        // the admin can change it
        mvc.perform(patch("/api/v1/jobs/" + id).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Principal Engineer\",\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Principal Engineer"));
    }

    @Test
    void updateRespectsVersionsAuditsFieldNamesAndRevalidatesSalary() throws Exception {
        JsonNode client = createClient("Northwind Fintech");
        JsonNode job = created(recruiterA, "/api/v1/jobs", jobBody(UUID.fromString(client.get("id").asText()), "Engineer"));
        String path = "/api/v1/jobs/" + job.get("id").asText();

        mvc.perform(patch(path).header("Authorization", recruiterA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"location\":\"Leeds\",\"openings\":3,\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value("Leeds"))
                .andExpect(jsonPath("$.openings").value(3));
        mvc.perform(patch(path).header("Authorization", recruiterA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"location\":\"Stale\",\"version\":0}"))
                .andExpect(status().isConflict());
        mvc.perform(patch(path).header("Authorization", recruiterA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"salaryMax\":10000,\"version\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("salaryMax"));
        List<String> details = jdbc.queryForList("select details::text from audit_log where action = 'JOB_UPDATED'", String.class);
        assertThat(details).singleElement().asString().contains("location", "openings").doesNotContain("Leeds");
    }

    @Test
    void jobSkillsCanBeReplaced() throws Exception {
        JsonNode client = createClient("Northwind Fintech");
        JsonNode job = created(recruiterA, "/api/v1/jobs", jobBody(UUID.fromString(client.get("id").asText()), "Engineer"));

        mvc.perform(put("/api/v1/jobs/" + job.get("id").asText() + "/skills").header("Authorization", recruiterA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("skills", List.of(
                                Map.of("skillId", skillId("aws"), "importance", "NICE_TO_HAVE", "weight", 2),
                                Map.of("skillId", skillId("docker"), "importance", "REQUIRED", "minYears", 2))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills.length()").value(2))
                .andExpect(jsonPath("$.skills[0].name").value("docker")) // required skills are listed first
                .andExpect(jsonPath("$.skills[1].weight").value(2));
    }

    @Test
    void searchFiltersByKeywordStatusClientOwnerLocationAndPages() throws Exception {
        UUID north = UUID.fromString(createClient("Northwind Fintech").get("id").asText());
        UUID atlas = UUID.fromString(createClient("Atlas Logistics").get("id").asText());
        createOpenJob(recruiterA, north, "Senior Java Engineer");
        JsonNode leeds = created(recruiterB, "/api/v1/jobs", jobBody(atlas, "Data Engineer"));
        mvc.perform(patch("/api/v1/jobs/" + leeds.get("id").asText()).header("Authorization", recruiterB)
                .contentType(MediaType.APPLICATION_JSON).content("{\"location\":\"Leeds\",\"version\":0}")).andExpect(status().isOk());
        created(recruiterA, "/api/v1/jobs", jobBody(atlas, "Frontend Developer"));

        assertThat(titles("q=engineer")).containsExactlyInAnyOrder("Senior Java Engineer", "Data Engineer");
        assertThat(titles("q=senior java")).containsExactly("Senior Java Engineer");
        assertThat(titles("status=OPEN")).containsExactly("Senior Java Engineer");
        assertThat(titles("status=DRAFT")).containsExactlyInAnyOrder("Data Engineer", "Frontend Developer");
        assertThat(titles("clientId=" + atlas)).containsExactlyInAnyOrder("Data Engineer", "Frontend Developer");
        assertThat(titles("ownerId=" + recruiterBId)).containsExactly("Data Engineer");
        assertThat(titles("location=leeds")).containsExactly("Data Engineer");
        assertThat(titles("q=%25")).isEmpty();

        mvc.perform(get("/api/v1/jobs?size=2&page=1&sort=title,asc").header("Authorization", recruiterA))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/api/v1/jobs?sort=ownerId,asc").header("Authorization", recruiterA))
                .andExpect(status().isBadRequest());
    }

    private List<String> titles(String query) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/jobs?" + query + "&size=100").header("Authorization", recruiterA)).andReturn();
        assertStatus(result, 200);
        List<String> titles = new java.util.ArrayList<>();
        body(result).get("content").forEach(j -> titles.add(j.get("title").asText()));
        return titles;
    }

    @Test
    void unknownJobIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/jobs/" + UUID.randomUUID()).header("Authorization", recruiterA))
                .andExpect(status().isNotFound());
    }
}
