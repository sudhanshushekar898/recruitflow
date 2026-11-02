package dev.recruitflow.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokensTest {

    @Test
    void generatedTokensAreUniqueUrlSafeAndLongEnough() {
        String a = Tokens.generate();
        String b = Tokens.generate();

        assertThat(a).isNotEqualTo(b).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void hashIsDeterministicHexAndNotTheRawToken() {
        String raw = Tokens.generate();

        assertThat(Tokens.hash(raw)).isEqualTo(Tokens.hash(raw)).matches("[0-9a-f]{64}").isNotEqualTo(raw);
        assertThat(Tokens.hash("a")).isNotEqualTo(Tokens.hash("b"));
    }
}
