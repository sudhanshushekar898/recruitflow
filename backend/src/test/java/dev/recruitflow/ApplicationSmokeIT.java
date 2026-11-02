package dev.recruitflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.recruitflow.identity.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Boots the whole app against a real PostgreSQL (never H2): migrations apply, probes are up, errors are well-formed. */
class ApplicationSmokeIT extends AbstractIntegrationTest {

    @Test
    void readinessProbeIsUpWithoutAuthentication() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void flywayMigrationsInstalledExtensions() {
        var extensions = jdbc.queryForList("select extname from pg_extension", String.class);
        assertThat(extensions).contains("citext", "pg_trgm");
    }

    @Test
    void unauthenticatedApiCallGetsProblemJsonWithCorrelationId() throws Exception {
        mvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void authenticatedUnknownRouteIsResourceNotFound() throws Exception {
        createUser("someone@example.com", STRONG_PASSWORD, Role.RECRUITER);
        mvc.perform(get("/api/v1/does-not-exist").header("Authorization", bearer("someone@example.com", STRONG_PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
