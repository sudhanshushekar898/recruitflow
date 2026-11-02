package dev.recruitflow.identity.repository;

import dev.recruitflow.identity.domain.OneTimeToken;
import dev.recruitflow.identity.domain.TokenPurpose;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OneTimeTokenRepository extends JpaRepository<OneTimeToken, UUID> {

    Optional<OneTimeToken> findByTokenHashAndPurpose(String tokenHash, TokenPurpose purpose);

    /** Atomic claim: returns 0 when another request already consumed the token. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update OneTimeToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
    int markUsed(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update OneTimeToken t set t.usedAt = :now where t.userId = :userId and t.purpose = :purpose and t.usedAt is null")
    int invalidateOutstanding(@Param("userId") UUID userId, @Param("purpose") TokenPurpose purpose, @Param("now") Instant now);
}
