package dev.recruitflow.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.recruitflow.AbstractIntegrationTest;
import dev.recruitflow.identity.domain.Role;
import dev.recruitflow.identity.domain.User;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AuthFlowIT extends AbstractIntegrationTest {

    private static final String EMAIL = "ana@example.com";

    @Test
    void loginReturnsAccessTokenAndHardenedRefreshCookie() throws Exception {
        createUser(EMAIL, STRONG_PASSWORD, Role.RECRUITER);

        MvcResult result = login("  ANA@Example.com ", STRONG_PASSWORD);

        assertStatus(result, 200);
        assertThat(body(result).get("accessToken").asText()).isNotBlank();
        assertThat(body(result).get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body(result).get("expiresInSeconds").asLong()).isEqualTo(900);
        assertThat(body(result).at("/user/roles/0").asText()).isEqualTo("RECRUITER");
        String cookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth");
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(auditCount("LOGIN_SUCCESS")).isEqualTo(1);
    }

    @Test
    void wrongPasswordAndUnknownEmailAreIndistinguishable() throws Exception {
        createUser(EMAIL, STRONG_PASSWORD, Role.RECRUITER);

        MvcResult wrongPassword = login(EMAIL, "not-the-password");
        MvcResult unknownEmail = login("nobody@example.com", "not-the-password");

        assertStatus(wrongPassword, 401);
        assertStatus(unknownEmail, 401);
        assertThat(body(wrongPassword).get("detail")).isEqualTo(body(unknownEmail).get("detail"));
        assertThat(body(wrongPassword).get("code").asText()).isEqualTo("UNAUTHENTICATED");
        assertThat(auditCount("LOGIN_FAILURE")).isEqualTo(2);
    }

    @Test
    void accountLocksAfterFiveFailuresEvenForTheCorrectPassword() throws Exception {
        createUser(EMAIL, STRONG_PASSWORD, Role.RECRUITER);

        for (int i = 0; i < 5; i++) {
            assertStatus(login(EMAIL, "wrong-password-" + i), 401);
        }
        MvcResult locked = login(EMAIL, STRONG_PASSWORD);

        assertStatus(locked, 423);
        assertThat(body(locked).get("code").asText()).isEqualTo("ACCOUNT_LOCKED");
        assertThat(auditCount("ACCOUNT_LOCKED")).isEqualTo(1);
    }

    @Test
    void lockExpiresAndCounterResets() throws Exception {
        User user = createUser(EMAIL, STRONG_PASSWORD, Role.RECRUITER);
        for (int i = 0; i < 5; i++) {
            login(EMAIL, "wrong-password-" + i);
        }
        jdbc.update("update users set locked_until = now() - interval '1 minute' where id = ?", user.getId());

        assertStatus(login(EMAIL, STRONG_PASSWORD), 200);
    }

    @Test
    void disabledUserCannotLogIn() throws Exception {
        User user = createUser(EMAIL, STRONG_PASSWORD, Role.RECRUITER);
        jdbc.update("update users set enabled = false where id = ?", user.getId());

        assertStatus(login(EMAIL, STRONG_PASSWORD), 401);
    }

    @Test
    void invitedUserWithoutPasswordCannotLogIn() throws Exception {
        users.save(User.invited(EMAIL, "Pending", "Person", java.util.Set.of(Role.RECRUITER)));

        assertStatus(login(EMAIL, STRONG_PASSWORD), 401);
    }

    @Test
    void refreshRotatesTheTokenAndIssuesAnotherAccessToken() throws Exception {
        createUser(EMAIL, STRONG_PASSWORD, Role.RECRUITER);
        String first = refreshCookieValue(login(EMAIL, STRONG_PASSWORD));

        MvcResult refreshed = refresh(first);

        assertStatus(refreshed, 200);
        assertThat(refreshCookieValue(refreshed)).isNotEqualTo(first);
        assertThat(body(refreshed).get("accessToken").asText()).isNotBlank();
    }

    @Test
    void reusingARotatedRefreshTokenRevokesTheWholeFamily() throws Exception {
        createUser(EMAIL, STRONG_PASSWORD, Role.RECRUITER);
        String first = refreshCookieValue(login(EMAIL, STRONG_PASSWORD));
        String second = refreshCookieValue(refresh(first));

        MvcResult replay = refresh(first);          // attacker replays the stolen, already-rotated token

        assertStatus(replay, 401);
        assertThat(auditCount("TOKEN_REUSE_DETECTED")).isEqualTo(1);
        assertStatus(refresh(second), 401);          // the legitimate holder is now logged out too
    }

    @Test
    void refreshRequiresTheScriptedRequestHeader() throws Exception {
        createUser(EMAIL, STRONG_PASSWORD, Role.RECRUITER);
        String token = refreshCookieValue(login(EMAIL, STRONG_PASSWORD));

        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void refreshWithoutCookieOrWithUnknownTokenIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
        assertStatus(refresh("totally-made-up-token"), 401);
    }

    @Test
    void expiredRefreshTokenIsRejected() throws Exception {
        createUser(EMAIL, STRONG_PASSWORD, Role.RECRUITER);
        String token = refreshCookieValue(login(EMAIL, STRONG_PASSWORD));
        jdbc.update("update refresh_tokens set expires_at = now() - interval '1 second'");

        assertStatus(refresh(token), 401);
    }

    @Test
    void logoutRevokesTheSessionAndClearsTheCookie() throws Exception {
        createUser(EMAIL, STRONG_PASSWORD, Role.RECRUITER);
        String token = refreshCookieValue(login(EMAIL, STRONG_PASSWORD));

        MvcResult logout = mvc.perform(post("/api/v1/auth/logout")
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(new Cookie("refresh_token", token))).andReturn();

        assertStatus(logout, 204);
        assertThat(logout.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        assertStatus(refresh(token), 401);
    }

    @Test
    void meReturnsTheSignedInUserAndRequiresAToken() throws Exception {
        createUser(EMAIL, STRONG_PASSWORD, Role.INTERVIEWER);

        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(EMAIL, STRONG_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.roles[0]").value("INTERVIEWER"));
    }

    @Test
    void tamperedOrGarbageBearerTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void userMustChangePasswordBeforeUsingAnythingElse() throws Exception {
        User admin = createUser(EMAIL, STRONG_PASSWORD, Role.ADMIN);
        jdbc.update("update users set must_change_password = true where id = ?", admin.getId());
        String bearer = bearer(EMAIL, STRONG_PASSWORD);

        mvc.perform(get("/api/v1/users").header("Authorization", bearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        MvcResult changed = mvc.perform(post("/api/v1/auth/change-password")
                .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currentPassword", STRONG_PASSWORD, "newPassword", "Brand-New-Passphrase-77"))))
                .andReturn();
        assertStatus(changed, 200);

        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + body(changed).get("accessToken").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void changingPasswordEndsOtherSessionsAndRejectsWrongCurrentPassword() throws Exception {
        createUser(EMAIL, STRONG_PASSWORD, Role.RECRUITER);
        MvcResult first = login(EMAIL, STRONG_PASSWORD);
        String otherSession = refreshCookieValue(login(EMAIL, STRONG_PASSWORD));
        String bearer = "Bearer " + body(first).get("accessToken").asText();

        MvcResult wrong = mvc.perform(post("/api/v1/auth/change-password").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currentPassword", "wrong-current-pw", "newPassword", "Brand-New-Passphrase-77"))))
                .andReturn();
        assertStatus(wrong, 422);

        MvcResult ok = mvc.perform(post("/api/v1/auth/change-password").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currentPassword", STRONG_PASSWORD, "newPassword", "Brand-New-Passphrase-77"))))
                .andReturn();
        assertStatus(ok, 200);
        assertStatus(refresh(otherSession), 401);
        assertStatus(login(EMAIL, STRONG_PASSWORD), 401);
        assertStatus(login(EMAIL, "Brand-New-Passphrase-77"), 200);
    }

    @Test
    void weakNewPasswordIsRejectedWithFieldError() throws Exception {
        createUser(EMAIL, STRONG_PASSWORD, Role.RECRUITER);

        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", bearer(EMAIL, STRONG_PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currentPassword", STRONG_PASSWORD, "newPassword", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
    }

    @Test
    void loginRequestWithMissingFieldsFailsValidation() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
