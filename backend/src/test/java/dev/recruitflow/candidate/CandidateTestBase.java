package dev.recruitflow.candidate;

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

/** Signs in a recruiter and an interviewer and offers helpers to create candidates through the real API. */
abstract class CandidateTestBase extends AbstractIntegrationTest {

    protected String recruiter;
    protected String interviewer;
    protected UUID recruiterId;

    @BeforeEach
    void createStaff() throws Exception {
        recruiterId = createUser("recruiter@example.com", STRONG_PASSWORD, Role.RECRUITER).getId();
        createUser("viewer@example.com", STRONG_PASSWORD, Role.INTERVIEWER);
        recruiter = bearer("recruiter@example.com", STRONG_PASSWORD);
        interviewer = bearer("viewer@example.com", STRONG_PASSWORD);
    }

    protected UUID skillId(String name) {
        return jdbc.queryForObject("select id from skills where name = ?", UUID.class, name);
    }

    protected Map<String, Object> candidateBody(String email) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstName", "Sam");
        body.put("lastName", "Patel");
        body.put("email", email);
        body.put("source", "REFERRAL");
        return body;
    }

    protected MvcResult postCandidate(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/v1/candidates").header("Authorization", recruiter)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andReturn();
    }

    protected JsonNode createCandidate(Map<String, Object> body) throws Exception {
        MvcResult result = postCandidate(body);
        assertStatus(result, 201);
        return body(result);
    }

    protected JsonNode createCandidate(String firstName, String lastName, String email, String title, String location,
                                       int years, String... skills) throws Exception {
        Map<String, Object> body = candidateBody(email);
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("currentTitle", title);
        body.put("location", location);
        body.put("yearsExperience", years);
        body.put("skills", java.util.Arrays.stream(skills).map(s -> Map.of("skillId", skillId(s), "years", 3)).toList());
        return createCandidate(body);
    }

    protected List<String> names(JsonNode page) {
        List<String> names = new java.util.ArrayList<>();
        page.get("content").forEach(c -> names.add(c.get("fullName").asText()));
        return names;
    }
}
