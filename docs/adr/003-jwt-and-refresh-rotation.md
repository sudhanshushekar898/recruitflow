# ADR-003: RS256 access tokens and rotating refresh tokens with reuse detection

**Status:** Accepted (M1)

## Context
A browser SPA needs authentication that limits the damage of token theft (XSS, stolen cookies, leaked database).

## Decision
- **Access token:** JWT signed RS256, 15 minutes, validated by Spring's OAuth2 Resource Server. Held in memory by the SPA, sent as a Bearer header. Keys come from `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY` (PEM); startup fails without them unless `allow-ephemeral-key` is set (local and test only).
- **Refresh token:** opaque 256-bit random value in an `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth` cookie. Only its SHA-256 hash is stored. Each use rotates it. Presenting an already-rotated token revokes the whole family (`TOKEN_REUSE_DETECTED` is audited). The cookie-authenticated endpoints (`refresh`, `logout`) also require an `X-Requested-With` header.
- **Lockout:** 5 consecutive failures lock the account for 15 minutes (423). Unknown emails and wrong passwords return the same 401 and spend the same hashing time.
- **Forced password change:** the bootstrap admin gets a token carrying an `mcp` claim; a filter allows only change-password, me and logout until it is changed.
- **Passwords:** BCrypt cost 12 behind `DelegatingPasswordEncoder`, minimum 12 characters, 72-byte maximum, small blocklist.

## Alternatives
- Keycloak / Cognito: stronger in production, but hides the fundamentals this project exists to show. Remains the documented production alternative.
- Session cookies: simpler, but couples the SPA and API origins and needs CSRF tokens everywhere.
- Tokens in `localStorage`: rejected, readable by any XSS.

## Consequences
- A deactivated user or changed role keeps a valid access token for up to 15 minutes; refresh tokens are revoked immediately.
- Two simultaneous refreshes of one token will log that session out, so the SPA must refresh single-flight.
- `ACCOUNT_LOCKED` reveals that an account exists after five failures. Accepted for an internal tool; rate limiting arrives in M8.
- Account emails are sent synchronously after commit through `UserMailer`; M10 replaces this with the transactional outbox.
