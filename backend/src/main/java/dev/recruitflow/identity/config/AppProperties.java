package dev.recruitflow.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** baseUrl is the SPA origin used to build invite and reset links. */
@ConfigurationProperties("recruitflow.app")
public record AppProperties(
        @DefaultValue("http://localhost:5173") String baseUrl,
        @DefaultValue("no-reply@recruitflow.local") String mailFrom) {
}
