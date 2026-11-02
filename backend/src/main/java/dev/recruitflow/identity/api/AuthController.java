package dev.recruitflow.identity.api;

import dev.recruitflow.identity.CurrentUser;
import dev.recruitflow.identity.api.AuthDtos.AcceptInviteRequest;
import dev.recruitflow.identity.api.AuthDtos.ChangePasswordRequest;
import dev.recruitflow.identity.api.AuthDtos.ForgotPasswordRequest;
import dev.recruitflow.identity.api.AuthDtos.LoginRequest;
import dev.recruitflow.identity.api.AuthDtos.LoginResponse;
import dev.recruitflow.identity.api.AuthDtos.ResetPasswordRequest;
import dev.recruitflow.identity.service.AuthResult;
import dev.recruitflow.identity.service.AuthService;
import dev.recruitflow.identity.service.UserSummary;
import dev.recruitflow.shared.error.DomainException;
import dev.recruitflow.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private static final String COOKIE = "refresh_token";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthService auth;
    private final CurrentUser currentUser;

    AuthController(AuthService auth, CurrentUser currentUser) {
        this.auth = auth;
        this.currentUser = currentUser;
    }

    @PostMapping("/login")
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return withSession(auth.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    ResponseEntity<LoginResponse> refresh(@CookieValue(name = COOKIE, required = false) String refreshToken,
                                          HttpServletRequest http) {
        requireScriptedRequest(http);
        return withSession(auth.refresh(refreshToken));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = COOKIE, required = false) String refreshToken,
                                HttpServletRequest http) {
        requireScriptedRequest(http);
        auth.logout(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", 0).toString()).build();
    }

    @GetMapping("/me")
    UserSummary me() {
        return auth.me(currentUser.id());
    }

    @PostMapping("/change-password")
    ResponseEntity<LoginResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return withSession(auth.changePassword(currentUser.id(), request.currentPassword(), request.newPassword()));
    }

    @PostMapping("/accept-invite")
    ResponseEntity<Void> acceptInvite(@Valid @RequestBody AcceptInviteRequest request) {
        auth.acceptInvite(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        auth.requestPasswordReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        auth.resetPassword(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<LoginResponse> withSession(AuthResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(result.refreshToken(), result.refreshMaxAgeSeconds()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new LoginResponse(result.accessToken(), "Bearer", result.expiresInSeconds(), result.user()));
    }

    /** HttpOnly, Secure, SameSite=Strict and scoped to /auth so the browser never sends it to other endpoints. */
    private static ResponseCookie cookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAgeSeconds)
                .build();
    }

    /** Cookie-authenticated endpoints require a header that cross-site forms and simple requests cannot set. */
    private static void requireScriptedRequest(HttpServletRequest request) {
        if (!"XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            throw new DomainException(ErrorCode.FORBIDDEN, "Missing X-Requested-With header.");
        }
    }
}
