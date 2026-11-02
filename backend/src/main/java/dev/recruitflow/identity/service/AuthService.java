package dev.recruitflow.identity.service;

import dev.recruitflow.audit.AuditRecorder;
import dev.recruitflow.identity.config.AppProperties;
import dev.recruitflow.identity.config.SecurityProperties;
import dev.recruitflow.identity.domain.RefreshToken;
import dev.recruitflow.identity.domain.TokenPurpose;
import dev.recruitflow.identity.domain.User;
import dev.recruitflow.identity.repository.RefreshTokenRepository;
import dev.recruitflow.identity.repository.UserRepository;
import dev.recruitflow.shared.error.DomainException;
import dev.recruitflow.shared.error.ErrorCode;
import dev.recruitflow.shared.persistence.AfterCommit;
import dev.recruitflow.shared.web.ClientInfo;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login, refresh-token rotation with reuse detection, logout and password flows.
 * Methods that reject a request use noRollbackFor so lockout counters and revocations still commit.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final OneTimeTokenService oneTimeTokens;
    private final PasswordEncoder encoder;
    private final PasswordPolicy passwordPolicy;
    private final JwtService jwt;
    private final SecurityProperties properties;
    private final AppProperties app;
    private final UserMailer mailer;
    private final AuditRecorder audit;
    private final Clock clock;
    /** Compared against when the account is unknown, so response time does not reveal which emails exist. */
    private final String dummyHash;

    AuthService(UserRepository users, RefreshTokenRepository refreshTokens, OneTimeTokenService oneTimeTokens,
                PasswordEncoder encoder, PasswordPolicy passwordPolicy, JwtService jwt, SecurityProperties properties,
                AppProperties app, UserMailer mailer, AuditRecorder audit, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.oneTimeTokens = oneTimeTokens;
        this.encoder = encoder;
        this.passwordPolicy = passwordPolicy;
        this.jwt = jwt;
        this.properties = properties;
        this.app = app;
        this.mailer = mailer;
        this.audit = audit;
        this.clock = clock;
        this.dummyHash = encoder.encode("not-a-real-password-" + UUID.randomUUID());
    }

    @Transactional(noRollbackFor = DomainException.class)
    public AuthResult login(String email, String password) {
        Instant now = clock.instant();
        User user = users.findByEmail(Emails.normalize(email)).orElse(null);
        if (user == null) {
            encoder.matches(password, dummyHash);
            audit.record("LOGIN_FAILURE", null, "USER", null, Map.of("reason", "unknown_account"));
            throw badCredentials();
        }
        if (user.isLocked(now)) {
            audit.record("LOGIN_BLOCKED", user.getId(), "USER", user.getId());
            throw new DomainException(ErrorCode.ACCOUNT_LOCKED, "Too many failed attempts. Try again later.");
        }
        boolean passwordMatches = encoder.matches(password, user.hasPassword() ? user.getPasswordHash() : dummyHash)
                && user.hasPassword();
        if (!passwordMatches || !user.isEnabled()) {
            if (user.isEnabled() && user.hasPassword()) {
                boolean locked = user.recordFailedLogin(now, properties.maxFailedLogins(), properties.lockDuration());
                audit.record("LOGIN_FAILURE", user.getId(), "USER", user.getId(), Map.of("reason", "bad_password"));
                if (locked) {
                    audit.record("ACCOUNT_LOCKED", user.getId(), "USER", user.getId());
                }
            } else {
                audit.record("LOGIN_FAILURE", user.getId(), "USER", user.getId(), Map.of("reason", "disabled_or_pending"));
            }
            throw badCredentials();
        }
        user.recordSuccessfulLogin(now);
        AuthResult result = startSession(user, UUID.randomUUID(), now);
        audit.record("LOGIN_SUCCESS", user.getId(), "USER", user.getId());
        return result;
    }

    /** Exchanges a refresh token for a new pair. Presenting an already-used token revokes the whole family. */
    @Transactional(noRollbackFor = DomainException.class)
    public AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw unauthenticated();
        }
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findForUpdateByHash(Tokens.hash(rawRefreshToken))
                .orElseThrow(AuthService::unauthenticated);
        UUID userId = current.getUserId();
        UUID familyId = current.getFamilyId();
        if (current.isRevoked()) {
            refreshTokens.revokeFamily(familyId, now);
            audit.record("TOKEN_REUSE_DETECTED", userId, "USER", userId, Map.of("familyId", familyId.toString()));
            throw unauthenticated();
        }
        if (current.isExpired(now)) {
            throw unauthenticated();
        }
        User user = users.findById(userId).filter(User::isEnabled).orElse(null);
        if (user == null) {
            refreshTokens.revokeFamily(familyId, now);
            throw unauthenticated();
        }
        String raw = Tokens.generate();
        RefreshToken next = newRefreshToken(user, familyId, raw, now);
        current.rotate(now, next.getId());
        return result(user, raw);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findForUpdateByHash(Tokens.hash(rawRefreshToken)).ifPresent(token -> {
            refreshTokens.revokeFamily(token.getFamilyId(), clock.instant());
            audit.record("LOGOUT", token.getUserId(), "USER", token.getUserId());
        });
    }

    @Transactional(readOnly = true)
    public UserSummary me(UUID userId) {
        return users.findById(userId).filter(User::isEnabled).map(UserSummary::from)
                .orElseThrow(AuthService::unauthenticated);
    }

    @Transactional
    public AuthResult changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = users.findById(userId).filter(User::isEnabled).orElseThrow(AuthService::unauthenticated);
        if (!user.hasPassword() || !encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new DomainException(ErrorCode.INVALID_OPERATION, "The current password is incorrect.");
        }
        passwordPolicy.validate("newPassword", newPassword);
        if (newPassword.equals(currentPassword)) {
            throw new DomainException(ErrorCode.INVALID_OPERATION, "The new password must differ from the current one.");
        }
        Instant now = clock.instant();
        user.setPassword(encoder.encode(newPassword));
        refreshTokens.revokeAllForUser(userId, now); // flushes the new hash, ends every other session
        AuthResult result = startSession(user, UUID.randomUUID(), now);
        audit.record("PASSWORD_CHANGED", userId, "USER", userId);
        return result;
    }

    @Transactional
    public void acceptInvite(String rawToken, String password) {
        passwordPolicy.validate("password", password);
        UUID userId = oneTimeTokens.consume(rawToken, TokenPurpose.INVITE);
        User user = users.findById(userId).filter(User::isEnabled).orElseThrow(AuthService::invalidToken);
        user.setPassword(encoder.encode(password));
        audit.record("INVITE_ACCEPTED", userId, "USER", userId);
    }

    /** Always completes silently so the endpoint cannot be used to discover which emails have accounts. */
    @Transactional
    public void requestPasswordReset(String email) {
        users.findByEmail(Emails.normalize(email)).filter(User::isEnabled).ifPresent(user -> {
            String raw = oneTimeTokens.issue(user.getId(), TokenPurpose.PASSWORD_RESET, properties.resetTtl());
            String link = app.baseUrl() + "/reset-password?token=" + raw;
            AfterCommit.run(() -> mailer.sendPasswordReset(user.getId(), user.getEmail(), user.getFirstName(), link));
            audit.record("PASSWORD_RESET_REQUESTED", user.getId(), "USER", user.getId());
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        passwordPolicy.validate("password", newPassword);
        UUID userId = oneTimeTokens.consume(rawToken, TokenPurpose.PASSWORD_RESET);
        User user = users.findById(userId).filter(User::isEnabled).orElseThrow(AuthService::invalidToken);
        user.setPassword(encoder.encode(newPassword));
        refreshTokens.revokeAllForUser(userId, clock.instant());
        audit.record("PASSWORD_RESET_COMPLETED", userId, "USER", userId);
    }

    private AuthResult startSession(User user, UUID familyId, Instant now) {
        String raw = Tokens.generate();
        newRefreshToken(user, familyId, raw, now);
        return result(user, raw);
    }

    private RefreshToken newRefreshToken(User user, UUID familyId, String raw, Instant now) {
        ClientInfo client = ClientInfo.current();
        return refreshTokens.save(new RefreshToken(user.getId(), Tokens.hash(raw), familyId,
                now.plus(properties.refreshTokenTtl()), client.userAgent(), client.ip()));
    }

    private AuthResult result(User user, String rawRefreshToken) {
        return new AuthResult(jwt.issueAccessToken(user), jwt.accessTokenTtlSeconds(), rawRefreshToken,
                properties.refreshTokenTtl().toSeconds(), UserSummary.from(user));
    }

    private static DomainException badCredentials() {
        return new DomainException(ErrorCode.UNAUTHENTICATED, "Invalid email or password.");
    }

    private static DomainException unauthenticated() {
        return new DomainException(ErrorCode.UNAUTHENTICATED, "Authentication required.");
    }

    private static DomainException invalidToken() {
        return new DomainException(ErrorCode.INVALID_TOKEN, "This link is invalid or has expired.");
    }
}
