package dev.recruitflow.identity.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("recruitflow.security")
public record SecurityProperties(
        @DefaultValue Jwt jwt,
        @DefaultValue("14d") Duration refreshTokenTtl,
        @DefaultValue("5") int maxFailedLogins,
        @DefaultValue("15m") Duration lockDuration,
        @DefaultValue("24h") Duration inviteTtl,
        @DefaultValue("1h") Duration resetTtl) {

    /** Keys are PEM (PKCS#8 private, X.509 public). Without them, startup fails unless an ephemeral key is allowed. */
    public record Jwt(
            String privateKey,
            String publicKey,
            @DefaultValue("false") boolean allowEphemeralKey,
            @DefaultValue("recruitflow") String issuer,
            @DefaultValue("15m") Duration accessTokenTtl) {
    }
}
