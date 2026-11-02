package dev.recruitflow.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class CandidateIT extends CandidateTestBase {

    interface Builder {
        MockHttpServletRequestBuilder build(UUID id);
    }

    static Stream<Arguments> endpoints() {
        String json = "{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"a@example.com\",\"source\":\"OTHER\"}";
        return Stream.of(
                Arguments.of("search", (Builder) id -> get("/api/v1/candidates")),
                Arguments.of("create", (Builder) id -> post("/api/v1/candidates").contentType(MediaType.APPLICATION_JSON).content(json)),
                Arguments.of("get", (Builder) id -> get("/api/v1/candidates/" + id)),
                Arguments.of("update", (Builder) id -> patch("/api/v1/candidates/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")),
                Arguments.of("archive", (Builder) id -> post("/api/v1/candidates/" + id + "/archive")),
                Arguments.of("restore", (Builder) id -> post("/api/v1/candidates/" + id + "/restore")),
                Arguments.of("skills", (Builder) id -> put("/api/v1/candidates/" + id + "/skills").contentType(MediaType.APPLICATION_JSON).content("{\"skills\":[]}")),
                Arguments.of("notes", (Builder) id -> get("/api/v1/candidates/" + id + "/notes")),
                Arguments.of("addNote", (Builder) id -> post("/api/v1/candidates/" + id + "/notes").contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"x\"}")),
                Arguments.of("consent", (Builder) id -> put("/api/v1/candidates/" + id + "/consent").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalBasis\":\"CONSENT\",\"givenAt\":\"2026-01-01T00:00:00Z\"}")));
    }

    @ParameterizedTest(name = "anonymous cannot {0}")
    @MethodSource("endpoints")
    void anonymousIsUnauthorized(String name, Builder builder) throws Exception {
        mvc.perform(builder.build(UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "interviewer cannot {0} (access is scoped in M6)")
    @MethodSource("endpoints")
    void interviewersAreForbidden(String name, Builder builder) throws Exception {
        mvc.perform(builder.build(UUID.randomUUID()).header("Authorization", interviewer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void createStoresNormalisedDataOwnerSkillsAndDefaultRetention() throws Exception {
        Map<String, Object> body = candidateBody("Sam.Patel@Example.COM");
        body.put("firstName", " Sam ");
        body.put("phone", "+44 7700 900123");
        body.put("currentTitle", "Java Developer");
        body.put("location", "London");
        body.put("yearsExperience", 6);
        body.put("skills", List.of(Map.of("skillId", skillId("java"), "years", 6, "level", "ADVANCED"),
                Map.of("skillId", skillId("postgresql"))));

        MvcResult result = postCandidate(body);

        assertStatus(result, 201);
        JsonNode c = body(result);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/v1/candidates/" + c.get("id").asText());
        assertThat(c.get("email").asText()).isEqualTo("sam.patel@example.com");
        assertThat(c.get("firstName").asText()).isEqualTo("Sam");
        assertThat(c.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(c.get("ownerId").asText()).isEqualTo(recruiterId.toString());
        assertThat(c.get("ownerName").asText()).isEqualTo("Test User");
        assertThat(c.get("skills").get(0).get("displayName").asText()).isEqualTo("Java");
        assertThat(c.get("skills").get(0).get("level").asText()).isEqualTo("ADVANCED");
        assertThat(c.get("skills").size()).isEqualTo(2);
        Instant retention = Instant.parse(c.get("retentionUntil").asText());
        assertThat(Duration.between(Instant.now(), retention).toDays()).isBetween(720L, 735L); // about 24 months
        assertThat(auditCount("CANDIDATE_CREATED")).isEqualTo(1);
    }

    @Test
    void duplicateEmailIsRejectedIgnoringCaseAndPointsToTheExistingCandidate() throws Exception {
        JsonNode first = createCandidate(candidateBody("sam@example.com"));

        MvcResult duplicate = postCandidate(candidateBody("SAM@Example.com"));

        assertStatus(duplicate, 409);
        assertThat(body(duplicate).get("code").asText()).isEqualTo("DUPLICATE_CANDIDATE");
        assertThat(body(duplicate).get("existingCandidateId").asText()).isEqualTo(first.get("id").asText());
    }

    @Test
    void concurrentCreatesOfTheSameEmailYieldExactlyOneCandidate() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                Callable<Integer> task = () -> postCandidate(candidateBody("race@example.com")).getResponse().getStatus();
                results.add(pool.submit(task));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) {
                statuses.add(f.get());
            }
            assertThat(statuses.stream().filter(s -> s == 201)).hasSize(1);
            assertThat(statuses.stream().filter(s -> s == 409)).hasSize(5);
            assertThat(jdbc.queryForObject("select count(*) from candidates", Integer.class)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void invalidBodyReportsEveryFieldError() throws Exception {
        Map<String, Object> body = candidateBody("not-an-email");
        body.put("firstName", "");
        body.put("phone", "call me maybe");
        body.put("linkedinUrl", "http://insecure.example.com/in/sam");
        body.put("yearsExperience", -1);

        MvcResult result = postCandidate(body);

        assertStatus(result, 400);
        List<String> fields = new ArrayList<>();
        body(result).get("errors").forEach(e -> fields.add(e.get("field").asText()));
        assertThat(fields).contains("firstName", "email", "phone", "linkedinUrl", "yearsExperience");
    }

    @Test
    void unknownOrRepeatedSkillsAreRejected() throws Exception {
        Map<String, Object> unknown = candidateBody("a@example.com");
        unknown.put("skills", List.of(Map.of("skillId", UUID.randomUUID())));
        Map<String, Object> repeated = candidateBody("b@example.com");
        repeated.put("skills", List.of(Map.of("skillId", skillId("java")), Map.of("skillId", skillId("java"))));

        for (Map<String, Object> body : List.of(unknown, repeated)) {
            MvcResult result = postCandidate(body);
            assertStatus(result, 400);
            assertThat(body(result).at("/errors/0/field").asText()).isEqualTo("skills");
        }
        assertThat(jdbc.queryForObject("select count(*) from candidates", Integer.class)).isZero();
    }

    @Test
    void unknownCandidateIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/candidates/" + UUID.randomUUID()).header("Authorization", recruiter))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void updateChangesOnlyTheGivenFieldsClearsEmptyOnesAndAuditsFieldNames() throws Exception {
        JsonNode c = createCandidate("Sam", "Patel", "sam@example.com", "Java Developer", "London", 6);

        mvc.perform(patch("/api/v1/candidates/" + c.get("id").asText()).header("Authorization", recruiter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentTitle\":\"Tech Lead\",\"location\":\"\",\"version\":" + c.get("version").asLong() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTitle").value("Tech Lead"))
                .andExpect(jsonPath("$.location").doesNotExist())
                .andExpect(jsonPath("$.firstName").value("Sam"))
                .andExpect(jsonPath("$.version").value(c.get("version").asLong() + 1));

        List<String> details = jdbc.queryForList("select details::text from audit_log where action = 'CANDIDATE_UPDATED'", String.class);
        assertThat(details).singleElement().asString().contains("currentTitle", "location").doesNotContain("Tech Lead");
    }

    @Test
    void staleVersionIsRejected() throws Exception {
        JsonNode c = createCandidate(candidateBody("sam@example.com"));
        String path = "/api/v1/candidates/" + c.get("id").asText();

        mvc.perform(patch(path).header("Authorization", recruiter).contentType(MediaType.APPLICATION_JSON)
                .content("{\"summary\":\"first\",\"version\":0}")).andExpect(status().isOk());
        mvc.perform(patch(path).header("Authorization", recruiter).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"second\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    @Test
    void changingEmailToAnExistingOneIsRejectedButKeepingYourOwnIsFine() throws Exception {
        createCandidate(candidateBody("taken@example.com"));
        JsonNode mine = createCandidate(candidateBody("mine@example.com"));
        String path = "/api/v1/candidates/" + mine.get("id").asText();

        mvc.perform(patch(path).header("Authorization", recruiter).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"TAKEN@example.com\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CANDIDATE"));
        mvc.perform(patch(path).header("Authorization", recruiter).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"MINE@example.com\",\"version\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    void archivedCandidatesLeaveTheDefaultListCannotBeEditedAndCanBeRestored() throws Exception {
        JsonNode c = createCandidate(candidateBody("sam@example.com"));
        String id = c.get("id").asText();

        mvc.perform(post("/api/v1/candidates/" + id + "/archive").header("Authorization", recruiter))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        mvc.perform(post("/api/v1/candidates/" + id + "/archive").header("Authorization", recruiter))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/candidates").header("Authorization", recruiter)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/candidates?status=ARCHIVED").header("Authorization", recruiter)).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(patch("/api/v1/candidates/" + id).header("Authorization", recruiter).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"x\",\"version\":" + jdbcVersion(id) + "}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_OPERATION"));

        mvc.perform(post("/api/v1/candidates/" + id + "/restore").header("Authorization", recruiter))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(post("/api/v1/candidates/" + id + "/restore").header("Authorization", recruiter))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/candidates").header("Authorization", recruiter)).andExpect(jsonPath("$.totalElements").value(1));
        assertThat(auditCount("CANDIDATE_ARCHIVED")).isEqualTo(1);
        assertThat(auditCount("CANDIDATE_RESTORED")).isEqualTo(1);
    }

    @Test
    void replacingSkillsOverwritesTheWholeSet() throws Exception {
        JsonNode c = createCandidate("Sam", "Patel", "sam@example.com", "Dev", "London", 3, "java", "docker");

        mvc.perform(put("/api/v1/candidates/" + c.get("id").asText() + "/skills").header("Authorization", recruiter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("skills", List.of(Map.of("skillId", skillId("aws"), "level", "EXPERT"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills.length()").value(1))
                .andExpect(jsonPath("$.skills[0].name").value("aws"))
                .andExpect(jsonPath("$.skills[0].level").value("EXPERT"));
    }

    @Test
    void notesAreListedNewestFirstWithTheAuthorsName() throws Exception {
        JsonNode c = createCandidate(candidateBody("sam@example.com"));
        String path = "/api/v1/candidates/" + c.get("id").asText() + "/notes";
        for (String text : List.of("first call", "second call")) {
            mvc.perform(post(path).header("Authorization", recruiter).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("body", text))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.authorName").value("Test User"));
        }

        mvc.perform(get(path).header("Authorization", recruiter))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].body").value("second call"));
        mvc.perform(post(path).header("Authorization", recruiter).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/candidates/" + UUID.randomUUID() + "/notes").header("Authorization", recruiter))
                .andExpect(status().isNotFound());
        assertThat(auditCount("CANDIDATE_NOTE_ADDED")).isEqualTo(2);
    }

    @Test
    void consentExpiryDrivesTheRetentionDateAndWithdrawalIsRecorded() throws Exception {
        JsonNode c = createCandidate(candidateBody("sam@example.com"));
        String path = "/api/v1/candidates/" + c.get("id").asText() + "/consent";

        mvc.perform(put(path).header("Authorization", recruiter).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalBasis\":\"CONSENT\",\"givenAt\":\"2026-10-01T09:00:00Z\",\"expiresAt\":\"2027-10-01T09:00:00Z\",\"evidence\":\"email thread\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consent.legalBasis").value("CONSENT"))
                .andExpect(jsonPath("$.consent.evidence").value("email thread"))
                .andExpect(jsonPath("$.retentionUntil").value("2027-10-01T09:00:00Z"));

        mvc.perform(put(path).header("Authorization", recruiter).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalBasis\":\"CONSENT\",\"givenAt\":\"2026-10-01T09:00:00Z\",\"withdrawn\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consent.withdrawnAt").exists());

        mvc.perform(put(path).header("Authorization", recruiter).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalBasis\":\"CONSENT\",\"givenAt\":\"2026-10-01T09:00:00Z\",\"expiresAt\":\"2026-09-01T09:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("expiresAt"));
        assertThat(auditCount("CANDIDATE_CONSENT_CHANGED")).isEqualTo(2);
    }

    @Test
    void consentCanBeSuppliedWhenCreating() throws Exception {
        Map<String, Object> body = candidateBody("sam@example.com");
        body.put("consent", Map.of("legalBasis", "LEGITIMATE_INTEREST", "givenAt", "2026-11-01T09:00:00Z"));

        JsonNode c = createCandidate(body);

        assertThat(c.at("/consent/legalBasis").asText()).isEqualTo("LEGITIMATE_INTEREST");
    }

    @Test
    void searchCombinesKeywordSkillLocationExperienceAndSourceFilters() throws Exception {
        createCandidate("Ana", "Lopez", "ana@example.com", "Java Developer", "London", 8, "java", "postgresql", "aws");
        createCandidate("Ben", "Okafor", "ben@example.com", "QA Engineer", "Leeds", 3, "java", "testing");
        createCandidate("Cho", "Park", "cho@example.com", "Java Architect", "London", 12, "java", "kubernetes");
        createCandidate("Dee", "Smith", "dee@example.com", "Data Analyst", "Berlin", 5, "sql", "postgresql");

        // Keyword search covers name, email, title, company and location (skills use the skillId filter).
        assertThat(search("q=java")).containsExactlyInAnyOrder("Ana Lopez", "Cho Park");
        assertThat(search("q=JAVA london")).containsExactlyInAnyOrder("Ana Lopez", "Cho Park");
        assertThat(search("q=london architect")).containsExactly("Cho Park");
        assertThat(search("q=okafor")).containsExactly("Ben Okafor");
        assertThat(search("q=example.com&location=lee")).containsExactly("Ben Okafor");
        assertThat(search("skillId=" + skillId("java") + "&skillId=" + skillId("postgresql"))).containsExactly("Ana Lopez");
        assertThat(search("skillId=" + skillId("postgresql"))).containsExactlyInAnyOrder("Ana Lopez", "Dee Smith");
        assertThat(search("minYears=8")).containsExactlyInAnyOrder("Ana Lopez", "Cho Park");
        assertThat(search("source=REFERRAL&minYears=12")).containsExactly("Cho Park");
        assertThat(search("source=LINKEDIN")).isEmpty();
        assertThat(search("ownerId=" + recruiterId)).hasSize(4);
        assertThat(search("ownerId=" + UUID.randomUUID())).isEmpty();
    }

    @Test
    void searchTreatsWildcardsLiterallyAndRejectsBadSortFields() throws Exception {
        createCandidate("Ana", "Lopez", "ana@example.com", "Java Developer", "London", 8);

        assertThat(search("q=%25")).isEmpty();
        assertThat(search("q=_")).isEmpty();
        mvc.perform(get("/api/v1/candidates?sort=email,asc").header("Authorization", recruiter))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void listIsPagedAndSortedWithACappedPageSize() throws Exception {
        for (char letter = 'a'; letter <= 'e'; letter++) {
            createCandidate("N" + letter, "Last" + letter, letter + "@example.com", "Dev", "London", 1);
        }

        mvc.perform(get("/api/v1/candidates?size=2&page=1&sort=lastName,desc").header("Authorization", recruiter))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].fullName").value("Nc Lastc"))
                .andExpect(jsonPath("$.content[1].fullName").value("Nb Lastb"))
                .andExpect(jsonPath("$.content[0].ownerName").value("Test User"));
        mvc.perform(get("/api/v1/candidates?size=5000").header("Authorization", recruiter))
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void listShowsSkillNamesForEachCandidate() throws Exception {
        createCandidate("Ana", "Lopez", "ana@example.com", "Dev", "London", 8, "postgresql", "java");

        mvc.perform(get("/api/v1/candidates").header("Authorization", recruiter))
                .andExpect(jsonPath("$.content[0].skills[0]").value("Java"))
                .andExpect(jsonPath("$.content[0].skills[1]").value("PostgreSQL"));
    }

    private List<String> search(String query) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/candidates?" + query + "&size=100").header("Authorization", recruiter)).andReturn();
        assertStatus(result, 200);
        return names(body(result));
    }

    private long jdbcVersion(String id) {
        Long version = jdbc.queryForObject("select version from candidates where id = ?::uuid", Long.class, id);
        return version == null ? 0 : version;
    }
}
