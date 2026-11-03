package dev.recruitflow.shared.persistence;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
class JpaConfig {

    /**
     * Ticks in whole microseconds, the precision PostgreSQL stores, so a timestamp returned right after it is
     * saved equals the one read back later.
     */
    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), java.time.Duration.ofNanos(1_000));
    }

    /** Fills createdAt/updatedAt with UTC instants from the application clock. */
    @Bean
    DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.<TemporalAccessor>of(Instant.now(clock));
    }
}
