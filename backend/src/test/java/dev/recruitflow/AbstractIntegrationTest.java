package dev.recruitflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.recruitflow.identity.domain.Role;
import dev.recruitflow.identity.domain.User;
import dev.recruitflow.identity.repository.UserRepository;
import dev.recruitflow.identity.service.UserMailer;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

/** One PostgreSQL container shared by all integration tests; tables are emptied before each test. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    public static final String STRONG_PASSWORD = "Correct-Horse-Battery-9";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UserRepository users;
    @Autowired protected PasswordEncoder encoder;
    @MockitoBean protected UserMailer mailer;

    @BeforeEach
    void cleanDatabase() {
        // Skills are seeded reference data and are kept; everything else starts empty.
        jdbc.execute("TRUNCATE interview_feedback, interview_participants, interviews, application_status_history, applications, job_skills, jobs, clients, "
                + "candidate_notes, candidate_skills, candidate_consents, candidates, "
                + "refresh_tokens, one_time_tokens, user_roles, users, audit_log CASCADE");
        jdbc.execute("DELETE FROM skills WHERE created_at > now() - interval '1 day' AND name LIKE 'test-%'");
    }

    protected User createUser(String email, String password, Role... roles) {
        User user = User.invited(email, "Test", "User", Set.copyOf(Arrays.asList(roles)));
        user.setPassword(encoder.encode(password));
        return users.save(user);
    }

    protected MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password)))).andReturn();
    }

    /** Logs in and returns the bearer header value, failing the test if login is refused. */
    protected String bearer(String email, String password) throws Exception {
        MvcResult result = login(email, password);
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return "Bearer " + body(result).get("accessToken").asText();
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    /** Extracts the refresh token value from the Set-Cookie header. */
    protected String refreshCookieValue(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        org.assertj.core.api.Assertions.assertThat(header).as("Set-Cookie").isNotNull();
        return header.split(";", 2)[0].substring("refresh_token=".length());
    }

    protected MvcResult refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh")
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(new Cookie("refresh_token", refreshToken))).andReturn();
    }

    protected void assertStatus(MvcResult result, int expected) throws Exception {
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                .as(result.getResponse().getContentAsString()).isEqualTo(expected);
    }

    protected int auditCount(String action) {
        Integer count = jdbc.queryForObject("select count(*) from audit_log where action = ?", Integer.class, action);
        return count == null ? 0 : count;
    }
}
