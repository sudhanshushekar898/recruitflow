package dev.recruitflow.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.recruitflow.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DemoDataSeederIT extends AbstractIntegrationTest {

    @Autowired
    DemoDataSeeder seeder;

    @Test
    void loadsARealisticDatasetOnce() {
        DemoDataSeeder.Summary summary = seeder.seed();

        assertThat(summary).isNotNull();
        assertThat(summary.users()).isEqualTo(5);
        assertThat(summary.clients()).isEqualTo(8);
        assertThat(summary.jobs()).isEqualTo(14);
        assertThat(summary.candidates()).isBetween(130, 150);
        assertThat(summary.applications()).isBetween(80, 250);
        assertThat(seeder.seed()).as("second run does nothing").isNull();
        assertThat(count("clients")).isEqualTo(8);
    }

    @Test
    void everyApplicationsHistoryIsConsistentWithItsStatus() {
        seeder.seed();

        // the last history step always equals the current status
        assertThat(jdbc.queryForObject("""
                select count(*) from applications a
                where a.status <> (select h.to_status from application_status_history h
                                   where h.application_id = a.id order by h.changed_at desc, h.id desc limit 1)""", Integer.class)).isZero();
        // every application starts at NEW and its history never goes back in time
        assertThat(jdbc.queryForObject("""
                select count(*) from applications a
                where (select h.to_status from application_status_history h where h.application_id = a.id
                       order by h.changed_at asc, h.id asc limit 1) <> 'NEW'""", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from application_status_history h
                where h.changed_at > now() + interval '1 minute'""", Integer.class)).isZero();
        // every step is a legal move in the pipeline
        List<String> illegal = jdbc.queryForList("""
                select from_status || '>' || to_status from application_status_history
                where from_status is not null
                  and (from_status || '>' || to_status) not in (
                    'NEW>SCREENING','NEW>SHORTLISTED','NEW>REJECTED','NEW>WITHDRAWN','SCREENING>SHORTLISTED','SCREENING>REJECTED',
                    'SCREENING>WITHDRAWN','SHORTLISTED>INTERVIEW','SHORTLISTED>REJECTED','SHORTLISTED>WITHDRAWN',
                    'INTERVIEW>OFFER','INTERVIEW>REJECTED','INTERVIEW>WITHDRAWN','OFFER>HIRED','OFFER>REJECTED','OFFER>WITHDRAWN',
                    'REJECTED>NEW')""", String.class);
        assertThat(illegal).isEmpty();
    }

    @Test
    void jobsAndPipelineLookLikeARealAgency() {
        seeder.seed();

        assertThat(jdbc.queryForList("select distinct status from jobs", String.class)).containsExactlyInAnyOrder("OPEN", "ON_HOLD", "CLOSED", "DRAFT");
        assertThat(jdbc.queryForList("select distinct status from applications", String.class))
                .contains("NEW", "SCREENING", "SHORTLISTED", "INTERVIEW", "OFFER", "HIRED", "REJECTED", "WITHDRAWN");
        // never more hires than openings, and every closed job filled something
        assertThat(jdbc.queryForObject("""
                select count(*) from jobs j where (select count(*) from applications a where a.job_id = j.id and a.status = 'HIRED') > j.openings""",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from jobs j where j.status = 'CLOSED'
                  and not exists (select 1 from applications a where a.job_id = j.id and a.status = 'HIRED')""", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from jobs where status = 'DRAFT' and exists (select 1 from applications a where a.job_id = jobs.id)",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from candidate_skills", Integer.class)).isGreaterThan(400);
    }

    @Test
    void usesOnlyTheReservedExampleDomainForCandidates() {
        seeder.seed();

        assertThat(jdbc.queryForObject("select count(*) from candidates where email not like '%@example.com'", Integer.class)).isZero();
    }

    @Test
    void demoRecruitersCanSignInAndSeeTheData() throws Exception {
        seeder.seed();

        String bearer = bearer("maya.chen@demo.recruitflow.local", DemoDataSeeder.DEMO_PASSWORD);

        mvc.perform(get("/api/v1/jobs?status=OPEN&size=50").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(9));
        mvc.perform(get("/api/v1/candidates?q=java&size=5").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(5));
        mvc.perform(get("/api/v1/clients").header("Authorization", bearer))
                .andExpect(jsonPath("$.totalElements").value(8));
    }

    private int count(String table) {
        Integer n = jdbc.queryForObject("select count(*) from " + table, Integer.class);
        return n == null ? 0 : n;
    }
}
