package dev.recruitflow.identity.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.recruitflow.shared.error.DomainException;
import dev.recruitflow.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsALongPassphrase() {
        assertThatCode(() -> policy.validate("password", "Correct-Horse-Battery-9")).doesNotThrowAnyException();
    }

    @Test
    void rejectsShortPasswords() {
        assertThatThrownBy(() -> policy.validate("password", "short"))
                .isInstanceOfSatisfying(DomainException.class, e -> assertCode(e, ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> policy.validate("password", null)).isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsPasswordsOverBcryptsByteLimit() {
        assertThatThrownBy(() -> policy.validate("password", "a1B2".repeat(19))).isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsCommonAndRepetitivePasswords() {
        assertThatThrownBy(() -> policy.validate("password", "Password1234")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> policy.validate("password", "aaaaaaaaaaaaaaaa")).isInstanceOf(DomainException.class);
    }

    @Test
    void errorNamesTheOffendingField() {
        assertThatThrownBy(() -> policy.validate("newPassword", "short"))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.extensions().get("errors").toString()).contains("newPassword"));
    }

    private static void assertCode(DomainException e, ErrorCode code) {
        org.assertj.core.api.Assertions.assertThat(e.code()).isEqualTo(code);
    }
}
