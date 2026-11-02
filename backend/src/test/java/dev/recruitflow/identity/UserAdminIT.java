package dev.recruitflow.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.recruitflow.AbstractIntegrationTest;
import dev.recruitflow.identity.domain.Role;
import dev.recruitflow.identity.domain.User;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MvcResult;

class UserAdminIT extends AbstractIntegrationTest {

    private static final String ADMIN = "admin@example.com";

    /** Every /users endpoint, built for a given target user id. */
    static Stream<Arguments> endpoints() {
        return Stream.of(
                Arguments.of("list", (Builder) id -> get("/api/v1/users")),
                Arguments.of("create", (Builder) id -> post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@example.com\",\"firstName\":\"X\",\"lastName\":\"Y\",\"roles\":[\"RECRUITER\"]}")),
                Arguments.of("get", (Builder) id -> get("/api/v1/users/" + id)),
                Arguments.of("update", (Builder) id -> patch("/api/v1/users/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Z\",\"version\":0}")),
                Arguments.of("roles", (Builder) id -> put("/api/v1/users/" + id + "/roles")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"RECRUITER\"],\"version\":0}")),
                Arguments.of("deactivate", (Builder) id -> post("/api/v1/users/" + id + "/deactivate")),
                Arguments.of("activate", (Builder) id -> post("/api/v1/users/" + id + "/activate")));
    }

    interface Builder {
        MockHttpServletRequestBuilder build(UUID targetId);
    }

    @ParameterizedTest(name = "anonymous cannot {0}")
    @MethodSource("endpoints")
    void anonymousIsUnauthorized(String name, Builder builder) throws Exception {
        User target = createUser("target@example.com", STRONG_PASSWORD, Role.RECRUITER);

        mvc.perform(builder.build(target.getId())).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} is forbidden for recruiters and interviewers")
    @MethodSource("endpoints")
    void nonAdminsAreForbidden(String name, Builder builder) throws Exception {
        User target = createUser("target@example.com", STRONG_PASSWORD, Role.RECRUITER);
        createUser("recruiter@example.com", STRONG_PASSWORD, Role.RECRUITER);
        createUser("interviewer@example.com", STRONG_PASSWORD, Role.INTERVIEWER);

        for (String email : List.of("recruiter@example.com", "interviewer@example.com")) {
            mvc.perform(builder.build(target.getId()).header("Authorization", bearer(email, STRONG_PASSWORD)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        assertThat(users.count()).isEqualTo(3); // nothing was created or changed
    }

    @Test
    void adminCanCreateAUserWhoStartsPendingWithoutAPassword() throws Exception {
        createUser(ADMIN, STRONG_PASSWORD, Role.ADMIN);

        MvcResult created = mvc.perform(post("/api/v1/users").header("Authorization", bearer(ADMIN, STRONG_PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "Pat@Example.com", "firstName", " Pat ", "lastName", "Lee",
                        "roles", List.of("RECRUITER", "INTERVIEWER"))))).andReturn();

        assertStatus(created, 201);
        assertThat(created.getResponse().getHeader("Location")).startsWith("/api/v1/users/");
        assertThat(body(created).get("email").asText()).isEqualTo("pat@example.com");
        assertThat(body(created).get("firstName").asText()).isEqualTo("Pat");
        assertThat(body(created).get("pendingInvite").asBoolean()).isTrue();
        assertThat(body(created).toString()).doesNotContain("password");
        assertThat(auditCount("USER_CREATED")).isEqualTo(1);
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        createUser(ADMIN, STRONG_PASSWORD, Role.ADMIN);

        mvc.perform(post("/api/v1/users").header("Authorization", bearer(ADMIN, STRONG_PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ADMIN@example.com\",\"firstName\":\"A\",\"lastName\":\"B\",\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_USER"));
    }

    @Test
    void createValidatesTheBody() throws Exception {
        createUser(ADMIN, STRONG_PASSWORD, Role.ADMIN);

        mvc.perform(post("/api/v1/users").header("Authorization", bearer(ADMIN, STRONG_PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"firstName\":\"\",\"lastName\":\"B\",\"roles\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void unknownJsonPropertiesAreRejected() throws Exception {
        createUser(ADMIN, STRONG_PASSWORD, Role.ADMIN);

        mvc.perform(post("/api/v1/users").header("Authorization", bearer(ADMIN, STRONG_PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@example.com\",\"firstName\":\"A\",\"lastName\":\"B\",\"roles\":[\"ADMIN\"],\"enabled\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listSupportsSearchFiltersPagingAndSorting() throws Exception {
        createUser(ADMIN, STRONG_PASSWORD, Role.ADMIN);
        for (int i = 1; i <= 5; i++) {
            createUser("recruiter" + i + "@example.com", STRONG_PASSWORD, Role.RECRUITER);
        }
        createUser("viewer@example.com", STRONG_PASSWORD, Role.INTERVIEWER);
        String bearer = bearer(ADMIN, STRONG_PASSWORD);

        mvc.perform(get("/api/v1/users?role=RECRUITER&size=2&page=1&sort=email,asc").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].email").value("recruiter3@example.com"));

        mvc.perform(get("/api/v1/users?q=VIEW").header("Authorization", bearer))
                .andExpect(jsonPath("$.totalElements").value(1));
        // wildcard characters in the search term are literal, not patterns
        mvc.perform(get("/api/v1/users?q=%25").header("Authorization", bearer))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listRejectsUnknownSortFieldsAndCapsPageSize() throws Exception {
        createUser(ADMIN, STRONG_PASSWORD, Role.ADMIN);
        String bearer = bearer(ADMIN, STRONG_PASSWORD);

        mvc.perform(get("/api/v1/users?sort=passwordHash,asc").header("Authorization", bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/users?size=100000").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void updateProfileUsesOptimisticLocking() throws Exception {
        createUser(ADMIN, STRONG_PASSWORD, Role.ADMIN);
        User target = createUser("target@example.com", STRONG_PASSWORD, Role.RECRUITER);
        String bearer = bearer(ADMIN, STRONG_PASSWORD);

        mvc.perform(patch("/api/v1/users/" + target.getId()).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Renamed\",\"version\":" + target.getVersion() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Renamed"))
                .andExpect(jsonPath("$.version").value(target.getVersion() + 1));
        mvc.perform(patch("/api/v1/users/" + target.getId()).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Stale\",\"version\":" + target.getVersion() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    @Test
    void changingRolesEndsTheUsersSessionsAndIsAudited() throws Exception {
        createUser(ADMIN, STRONG_PASSWORD, Role.ADMIN);
        User target = createUser("target@example.com", STRONG_PASSWORD, Role.RECRUITER);
        String session = refreshCookieValue(login("target@example.com", STRONG_PASSWORD));
        User fresh = users.findById(target.getId()).orElseThrow();

        mvc.perform(put("/api/v1/users/" + target.getId() + "/roles").header("Authorization", bearer(ADMIN, STRONG_PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"INTERVIEWER\"],\"version\":" + fresh.getVersion() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("INTERVIEWER"))
                .andExpect(jsonPath("$.roles.length()").value(1));

        assertStatus(refresh(session), 401);
        assertThat(auditCount("USER_ROLES_CHANGED")).isEqualTo(1);
    }

    @Test
    void adminCannotRemoveTheirOwnAdminRoleOrDeactivateThemselves() throws Exception {
        User created = createUser(ADMIN, STRONG_PASSWORD, Role.ADMIN);
        String bearer = bearer(ADMIN, STRONG_PASSWORD);
        User admin = users.findById(created.getId()).orElseThrow(); // login bumped the version

        mvc.perform(put("/api/v1/users/" + admin.getId() + "/roles").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"RECRUITER\"],\"version\":" + admin.getVersion() + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_OPERATION"));
        mvc.perform(post("/api/v1/users/" + admin.getId() + "/deactivate").header("Authorization", bearer))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_OPERATION"));
    }

    @Test
    void deactivatingAUserRevokesTheirSessionsAndBlocksLoginUntilReactivated() throws Exception {
        createUser(ADMIN, STRONG_PASSWORD, Role.ADMIN);
        User target = createUser("target@example.com", STRONG_PASSWORD, Role.RECRUITER);
        String session = refreshCookieValue(login("target@example.com", STRONG_PASSWORD));
        String bearer = bearer(ADMIN, STRONG_PASSWORD);

        mvc.perform(post("/api/v1/users/" + target.getId() + "/deactivate").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        assertStatus(refresh(session), 401);
        assertStatus(login("target@example.com", STRONG_PASSWORD), 401);

        mvc.perform(post("/api/v1/users/" + target.getId() + "/activate").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
        assertStatus(login("target@example.com", STRONG_PASSWORD), 200);
        assertThat(auditCount("USER_DEACTIVATED")).isEqualTo(1);
    }

    @Test
    void unknownUserIdIsNotFound() throws Exception {
        createUser(ADMIN, STRONG_PASSWORD, Role.ADMIN);

        mvc.perform(get("/api/v1/users/" + UUID.randomUUID()).header("Authorization", bearer(ADMIN, STRONG_PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void auditLogCannotBeModifiedOrDeleted() throws Exception {
        createUser(ADMIN, STRONG_PASSWORD, Role.ADMIN);
        login(ADMIN, STRONG_PASSWORD);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("update audit_log set action = 'TAMPERED'"))
                .hasMessageContaining("append-only");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("delete from audit_log"))
                .hasMessageContaining("append-only");
    }
}
