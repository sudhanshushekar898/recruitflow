package dev.recruitflow.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final Duration LOCK = Duration.ofMinutes(15);

    private User user() {
        return User.invited("a@example.com", "A", "B", Set.of(Role.RECRUITER));
    }

    @Test
    void locksOnTheFifthConsecutiveFailure() {
        User user = user();

        for (int i = 0; i < 4; i++) {
            assertThat(user.recordFailedLogin(NOW, 5, LOCK)).isFalse();
        }

        assertThat(user.recordFailedLogin(NOW, 5, LOCK)).isTrue();
        assertThat(user.isLocked(NOW)).isTrue();
        assertThat(user.isLocked(NOW.plus(LOCK).plusSeconds(1))).isFalse();
    }

    @Test
    void successfulLoginClearsFailuresAndLock() {
        User user = user();
        for (int i = 0; i < 4; i++) {
            user.recordFailedLogin(NOW, 5, LOCK);
        }

        user.recordSuccessfulLogin(NOW);

        assertThat(user.recordFailedLogin(NOW, 5, LOCK)).isFalse(); // counter restarted
        assertThat(user.getLastLoginAt()).isEqualTo(NOW);
    }

    @Test
    void countingRestartsAfterAnExpiredLock() {
        User user = user();
        for (int i = 0; i < 5; i++) {
            user.recordFailedLogin(NOW, 5, LOCK);
        }
        Instant later = NOW.plus(LOCK).plusSeconds(1);

        assertThat(user.recordFailedLogin(later, 5, LOCK)).isFalse();
        assertThat(user.isLocked(later)).isFalse();
    }

    @Test
    void settingAPasswordClearsTheForcedChangeFlagAndLock() {
        User user = User.bootstrapAdmin("root@example.com", "{bcrypt}hash");
        assertThat(user.isMustChangePassword()).isTrue();

        user.setPassword("{bcrypt}newhash");

        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}newhash");
    }

    @Test
    void replaceRolesKeepsExactlyTheNewSet() {
        User user = User.invited("a@example.com", "A", "B", Set.of(Role.RECRUITER, Role.INTERVIEWER));

        user.replaceRoles(Set.of(Role.INTERVIEWER, Role.ADMIN));

        assertThat(user.getRoles()).containsExactlyInAnyOrder(Role.INTERVIEWER, Role.ADMIN);
    }

    @Test
    void invitedUserHasNoPasswordUntilOneIsSet() {
        assertThat(user().hasPassword()).isFalse();
    }
}
