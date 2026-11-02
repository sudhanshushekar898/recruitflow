package dev.recruitflow.candidate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class SkillIT extends CandidateTestBase {

    @Test
    void anonymousCannotListSkills() throws Exception {
        mvc.perform(get("/api/v1/skills")).andExpect(status().isUnauthorized());
    }

    @Test
    void seededSkillsAreListedAlphabeticallyAndCappedForAutocomplete() throws Exception {
        mvc.perform(get("/api/v1/skills").header("Authorization", interviewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(20))
                .andExpect(jsonPath("$[0].name").value("agile"));
    }

    @Test
    void autocompleteMatchesNameDisplayNameAndAlias() throws Exception {
        mvc.perform(get("/api/v1/skills?q=SPRING").header("Authorization", recruiter))
                .andExpect(jsonPath("$[?(@.name=='spring boot')]").exists())
                .andExpect(jsonPath("$[?(@.name=='spring')]").exists());
        mvc.perform(get("/api/v1/skills?q=k8s").header("Authorization", recruiter))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].displayName").value("Kubernetes"));
        mvc.perform(get("/api/v1/skills?q=postgres").header("Authorization", recruiter))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("postgresql"));
    }

    @Test
    void searchTreatsWildcardCharactersLiterally() throws Exception {
        mvc.perform(get("/api/v1/skills?q=%25").header("Authorization", recruiter))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void recruiterCanCreateASkillWhichIsNormalised() throws Exception {
        mvc.perform(post("/api/v1/skills").header("Authorization", recruiter).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Test-Graph   QL \",\"category\":\"Data\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("test-graph ql"))
                .andExpect(jsonPath("$.displayName").value("Test-Graph   QL"));
    }

    @Test
    void duplicateNameOrAliasIsRejected() throws Exception {
        mvc.perform(post("/api/v1/skills").header("Authorization", recruiter).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"JAVA\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SKILL"));
        mvc.perform(post("/api/v1/skills").header("Authorization", recruiter).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Postgres\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void interviewersCannotCreateSkills() throws Exception {
        mvc.perform(post("/api/v1/skills").header("Authorization", interviewer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test-forbidden\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void blankNameIsRejected() throws Exception {
        mvc.perform(post("/api/v1/skills").header("Authorization", recruiter).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
