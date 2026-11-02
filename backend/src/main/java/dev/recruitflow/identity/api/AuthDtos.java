package dev.recruitflow.identity.api;

import dev.recruitflow.identity.service.UserSummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request and response bodies of the /auth endpoints. */
final class AuthDtos {

    private AuthDtos() {}

    record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {
    }

    record LoginResponse(String accessToken, String tokenType, long expiresInSeconds, UserSummary user) {
    }

    record ChangePasswordRequest(@NotBlank @Size(max = 128) String currentPassword,
                                 @NotBlank @Size(max = 128) String newPassword) {
    }

    record AcceptInviteRequest(@NotBlank @Size(max = 200) String token, @NotBlank @Size(max = 128) String password) {
    }

    record ForgotPasswordRequest(@NotBlank @Size(max = 254) String email) {
    }

    record ResetPasswordRequest(@NotBlank @Size(max = 200) String token, @NotBlank @Size(max = 128) String password) {
    }
}
