package dev.recruitflow.workflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import dev.recruitflow.AbstractIntegrationTest;
import dev.recruitflow.identity.domain.Role;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** Staff with every role plus helpers that build clients, jobs, candidates and applications through the real API. */
abstract class WorkflowTestBase extends AbstractIntegrationTest {

    protected String admin;
    protected String recruiterA;
    protected String recruiterB;
    protected String interviewer;
    protected UUID recruiterAId;
    protected UUID recruiterBId;

    @BeforeEach
    void createStaff() throws Exception {
        createUser("admin@example.com", STRONG_PASSWORD, Role.ADMIN);
        recruiterAId = createUser("recruiter.a@example.com", STRONG_PASSWORD, Role.RECRUITER).getId();
        recruiterBId = createUser("recruiter.b@example.com", STRONG_PASSWORD, Role.RECRUITER).getId();
        createUser("viewer@example.com", STRONG_PASSWORD, Role.INTERVIEWER);
        admin = bearer("admin@example.com", STRONG_PASSWORD);
        recruiterA = bearer("recruiter.a@example.com", STRONG_PASSWORD);
        recruiterB = bearer("recruiter.b@example.com", STRONG_PASSWORD);
        interviewer = bearer("viewer@example.com", STRONG_PASSWORD);
    }

    protected UUID skillId(String name) {
        return jdbc.queryForObject("select id from skills where name = ?", UUID.class, name);
    }

    protected JsonNode send(String bearer, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, int expected) throws Exception {
        MvcResult result = mvc.perform(request.header("Authorization", bearer)).andReturn();
        assertStatus(result, expected);
        return result.getResponse().getContentAsString().isBlank() ? null : body(result);
    }

    protected MvcResult postJson(String bearer, String path, Object body) throws Exception {
        return mvc.perform(post(path).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andReturn();
    }

    protected JsonNode created(String bearer, String path, Object body) throws Exception {
        MvcResult result = postJson(bearer, path, body);
        assertStatus(result, 201);
        return body(result);
    }

    protected JsonNode createClient(String name) throws Exception {
        return created(recruiterA, "/api/v1/clients", Map.of("name", name, "industry", "Software"));
    }

    protected Map<String, Object> jobBody(UUID clientId, String title) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientId", clientId);
        body.put("title", title);
        body.put("location", "London");
        body.put("employmentType", "FULL_TIME");
        body.put("salaryMin", 60000);
        body.put("salaryMax", 80000);
        body.put("currency", "GBP");
        body.put("openings", 1);
        body.put("skills", List.of(Map.of("skillId", skillId("java"), "importance", "REQUIRED", "minYears", 3, "weight", 5)));
        return body;
    }

    /** Creates a job as the given user and moves it to OPEN. */
    protected JsonNode createOpenJob(String owner, UUID clientId, String title) throws Exception {
        JsonNode job = created(owner, "/api/v1/jobs", jobBody(clientId, title));
        MvcResult opened = postJson(owner, "/api/v1/jobs/" + job.get("id").asText() + "/status", Map.of("toStatus", "OPEN"));
        assertStatus(opened, 200);
        return body(opened);
    }

    protected JsonNode createCandidate(String email) throws Exception {
        return created(recruiterA, "/api/v1/candidates",
                Map.of("firstName", "Sam", "lastName", email.split("@")[0], "email", email, "source", "REFERRAL"));
    }

    protected JsonNode apply(String bearer, JsonNode candidate, JsonNode job) throws Exception {
        return created(bearer, "/api/v1/applications", Map.of("candidateId", candidate.get("id").asText(), "jobId", job.get("id").asText()));
    }

    protected MvcResult move(String bearer, JsonNode application, String to) throws Exception {
        return postJson(bearer, "/api/v1/applications/" + application.get("id").asText() + "/status",
                Map.of("toStatus", to, "version", application.get("version").asLong()));
    }

    /** Moves and returns the updated application, failing the test unless the move succeeds. */
    protected JsonNode moved(String bearer, JsonNode application, String to) throws Exception {
        MvcResult result = move(bearer, application, to);
        assertStatus(result, 200);
        return body(result).get("application");
    }
}
