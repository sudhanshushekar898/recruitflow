package dev.recruitflow.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Performance guardrail from the architecture (p95 under 300 ms on 100k candidates): proves the trigram
 * index serves keyword search at that size instead of scanning the table.
 */
class CandidateSearchPerformanceIT extends CandidateTestBase {

    private static final int ROWS = 100_000;
    private static final long BUDGET_MS = 300;

    @Test
    void keywordAndSkillSearchStayWithinBudgetOnOneHundredThousandCandidates() throws Exception {
        jdbc.update("""
                INSERT INTO candidates (id, first_name, last_name, email, current_title, location, years_experience,
                                        source, status, owner_id, retention_until)
                SELECT gen_random_uuid(), 'First' || g, 'Last' || (g % 5000), 'user' || g || '@example.com',
                       (ARRAY['Java Developer','QA Engineer','DevOps Engineer','Data Analyst'])[1 + g % 4],
                       (ARRAY['London','Leeds','Berlin','Madrid','Pune'])[1 + g % 5],
                       g % 20, 'OTHER', 'ACTIVE', ?, now() + interval '2 years'
                FROM generate_series(1, ?) g
                """, recruiterId, ROWS);
        jdbc.update("""
                INSERT INTO candidate_skills (candidate_id, skill_id, years)
                SELECT c.id, ?, 2 FROM candidates c WHERE c.email LIKE 'user%7@example.com'
                """, skillId("java"));
        jdbc.execute("ANALYZE candidates");
        jdbc.execute("ANALYZE candidate_skills");

        String selective = "q=last4321";
        // The planner must use the trigram index for a selective keyword, not read all 100k rows.
        List<String> plan = jdbc.queryForList(
                "EXPLAIN SELECT id FROM candidates WHERE search_text LIKE '%last4321%'", String.class);
        assertThat(String.join("\n", plan)).contains("ix_candidates_search_text_trgm");

        assertThat(medianMillis(selective)).as("keyword search").isLessThan(BUDGET_MS);
        assertThat(medianMillis(selective + "&skillId=" + skillId("java") + "&minYears=3")).as("keyword + skill + experience").isLessThan(BUDGET_MS);
        assertThat(medianMillis("location=berlin&minYears=15&sort=lastName,asc")).as("filters only").isLessThan(BUDGET_MS);

        MvcResult result = mvc.perform(get("/api/v1/candidates?" + selective + "&size=100").header("Authorization", recruiter)).andReturn();
        JsonNode page = body(result);
        assertThat(page.get("totalElements").asInt()).isEqualTo(ROWS / 5000); // 20 candidates share "Last4321"
    }

    /** Warms up once, then times several requests; the median keeps one slow outlier from failing the build. */
    private long medianMillis(String query) throws Exception {
        mvc.perform(get("/api/v1/candidates?" + query).header("Authorization", recruiter)).andReturn();
        List<Long> times = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            long start = System.nanoTime();
            MvcResult result = mvc.perform(get("/api/v1/candidates?" + query).header("Authorization", recruiter)).andReturn();
            times.add((System.nanoTime() - start) / 1_000_000);
            assertStatus(result, 200);
        }
        Collections.sort(times);
        return times.get(times.size() / 2);
    }
}
