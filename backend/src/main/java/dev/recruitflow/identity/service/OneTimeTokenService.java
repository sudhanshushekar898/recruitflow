package dev.recruitflow.identity.service;

import dev.recruitflow.identity.domain.OneTimeToken;
import dev.recruitflow.identity.domain.TokenPurpose;
import dev.recruitflow.identity.repository.OneTimeTokenRepository;
import dev.recruitflow.shared.error.DomainException;
import dev.recruitflow.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issues and consumes single-use invite / password-reset tokens. */
@Service
class OneTimeTokenService {

    private final OneTimeTokenRepository repository;
    private final Clock clock;

    OneTimeTokenService(OneTimeTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Replaces any outstanding token of the same purpose and returns the raw value to put in the email. */
    @Transactional
    String issue(UUID userId, TokenPurpose purpose, Duration ttl) {
        Instant now = clock.instant();
        repository.invalidateOutstanding(userId, purpose, now);
        String raw = Tokens.generate();
        repository.save(new OneTimeToken(userId, purpose, Tokens.hash(raw), now.plus(ttl)));
        return raw;
    }

    /** Atomically claims the token and returns its user id; throws INVALID_TOKEN if unknown, used or expired. */
    @Transactional
    UUID consume(String rawToken, TokenPurpose purpose) {
        Instant now = clock.instant();
        OneTimeToken token = repository.findByTokenHashAndPurpose(Tokens.hash(rawToken), purpose)
                .filter(t -> t.isUsable(now))
                .orElseThrow(OneTimeTokenService::invalid);
        UUID userId = token.getUserId();
        if (repository.markUsed(token.getId(), now) != 1) {
            throw invalid();
        }
        return userId;
    }

    private static DomainException invalid() {
        return new DomainException(ErrorCode.INVALID_TOKEN, "This link is invalid or has expired.");
    }
}
