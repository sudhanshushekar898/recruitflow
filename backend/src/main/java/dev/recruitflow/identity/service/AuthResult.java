package dev.recruitflow.identity.service;

/** Outcome of login, refresh and password change: a new access token, a new raw refresh token, and the user. */
public record AuthResult(String accessToken, long expiresInSeconds, String refreshToken, long refreshMaxAgeSeconds, UserSummary user) {
}
