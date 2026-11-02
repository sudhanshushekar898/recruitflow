package dev.recruitflow.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.recruitflow.AbstractIntegrationTest;
import dev.recruitflow.identity.domain.Role;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class PasswordFlowsIT extends AbstractIntegrationTest {

    private static final String EMAIL = "reset.me@example.com";
    private static final String NEW_PASSWORD = "A-Fresh-Passphrase-2026";

    @Test
    void forgotPasswordThenResetLetsTheUserLogInWithTheNewPassword() throws Exception {
        createUser(EMAIL, STRONG_PASSWORD, Role.RECRUITER);
        String staleSession = refreshCookieValue(login(EMAIL, STRONG_PASSWORD));

        postJson("/api/v1/auth/forgot-password", Map.of("email", EMAIL)).andExpect(status().isAccepted());
        String token = captureTokenFromLink(true);
        postJson("/api/v1/auth/reset-password", Map.of("token", token, "password", NEW_PASSWORD))
                .andExpect(status().isNoContent());

        assertStatus(login(EMAIL, STRONG_PASSWORD), 401);
        assertStatus(login(EMAIL, NEW_PASSWORD), 200);
        assertStatus(refresh(staleSession), 401);
        assertThat(auditCount("PASSWORD_RESET_COMPLETED")).isEqualTo(1);
    }

    @Test
    void resetTokenIsSingleUse() throws Exception {
        createUser(EMAIL, STRONG_PASSWORD, Role.RECRUITER);
        postJson("/api/v1/auth/forgot-password", Map.of("email", EMAIL));
        String token = captureTokenFromLink(true);

        postJson("/api/v1/auth/reset-password", Map.of("token", token, "password", NEW_PASSWORD))
                .andExpect(status().isNoContent());
        postJson("/api/v1/auth/reset-password", Map.of("token", token, "password", "Another-Passphrase-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void requestingAnotherResetInvalidatesTheEarlierLink() throws Exception {
        createUser(EMAIL, STRONG_PASSWORD, Role.RECRUITER);
        postJson("/api/v1/auth/forgot-password", Map.of("email", EMAIL));
        postJson("/api/v1/auth/forgot-password", Map.of("email", EMAIL));
        ArgumentCaptor<String> links = ArgumentCaptor.forClass(String.class);
        verify(mailer, org.mockito.Mockito.times(2)).sendPasswordReset(any(UUID.class), anyString(), anyString(), links.capture());

        String older = tokenOf(links.getAllValues().get(0));
        String newer = tokenOf(links.getAllValues().get(1));

        postJson("/api/v1/auth/reset-password", Map.of("token", older, "password", NEW_PASSWORD))
                .andExpect(status().isBadRequest());
        postJson("/api/v1/auth/reset-password", Map.of("token", newer, "password", NEW_PASSWORD))
                .andExpect(status().isNoContent());
    }

    @Test
    void expiredResetTokenIsRejected() throws Exception {
        createUser(EMAIL, STRONG_PASSWORD, Role.RECRUITER);
        postJson("/api/v1/auth/forgot-password", Map.of("email", EMAIL));
        String token = captureTokenFromLink(true);
        jdbc.update("update one_time_tokens set expires_at = now() - interval '1 second'");

        postJson("/api/v1/auth/reset-password", Map.of("token", token, "password", NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void weakPasswordDoesNotBurnTheResetToken() throws Exception {
        createUser(EMAIL, STRONG_PASSWORD, Role.RECRUITER);
        postJson("/api/v1/auth/forgot-password", Map.of("email", EMAIL));
        String token = captureTokenFromLink(true);

        postJson("/api/v1/auth/reset-password", Map.of("token", token, "password", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        postJson("/api/v1/auth/reset-password", Map.of("token", token, "password", NEW_PASSWORD))
                .andExpect(status().isNoContent());
    }

    @Test
    void forgotPasswordForUnknownEmailIsSilentlyAcceptedAndSendsNothing() throws Exception {
        postJson("/api/v1/auth/forgot-password", Map.of("email", "ghost@example.com")).andExpect(status().isAccepted());

        verify(mailer, never()).sendPasswordReset(any(), anyString(), anyString(), anyString());
    }

    @Test
    void invitedUserSetsAPasswordThroughTheInviteLinkAndThenLogsIn() throws Exception {
        createUser("boss@example.com", STRONG_PASSWORD, Role.ADMIN);
        String admin = bearer("boss@example.com", STRONG_PASSWORD);
        mvc.perform(post("/api/v1/users").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "New.Hire@Example.com", "firstName", "New", "lastName", "Hire",
                        "roles", java.util.List.of("RECRUITER"))))).andExpect(status().isCreated());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendInvite(any(UUID.class), org.mockito.ArgumentMatchers.eq("new.hire@example.com"), anyString(), link.capture());
        assertStatus(login("new.hire@example.com", NEW_PASSWORD), 401); // cannot log in before accepting

        postJson("/api/v1/auth/accept-invite", Map.of("token", tokenOf(link.getValue()), "password", NEW_PASSWORD))
                .andExpect(status().isNoContent());

        assertStatus(login("new.hire@example.com", NEW_PASSWORD), 200);
    }

    @Test
    void resetTokenCannotBeUsedAsInviteToken() throws Exception {
        createUser(EMAIL, STRONG_PASSWORD, Role.RECRUITER);
        postJson("/api/v1/auth/forgot-password", Map.of("email", EMAIL));
        String token = captureTokenFromLink(true);

        postJson("/api/v1/auth/accept-invite", Map.of("token", token, "password", NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    private ResultActions postJson(String path, Object body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private String captureTokenFromLink(boolean reset) {
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendPasswordReset(any(UUID.class), anyString(), anyString(), link.capture());
        return tokenOf(link.getValue());
    }

    private static String tokenOf(String link) {
        return link.substring(link.indexOf("token=") + "token=".length());
    }
}
