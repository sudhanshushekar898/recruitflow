package dev.recruitflow.identity.service;

import dev.recruitflow.identity.domain.Role;
import dev.recruitflow.identity.domain.User;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** The signed-in user as shown to the SPA. */
public record UserSummary(UUID id, String email, String name, List<Role> roles, boolean mustChangePassword) {

    static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getFullName(),
                user.getRoles().stream().sorted(Comparator.naturalOrder()).toList(), user.isMustChangePassword());
    }
}
