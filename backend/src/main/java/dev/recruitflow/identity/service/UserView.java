package dev.recruitflow.identity.service;

import dev.recruitflow.identity.domain.Role;
import dev.recruitflow.identity.domain.User;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Admin view of a user. Never includes password material. */
public record UserView(UUID id, String email, String firstName, String lastName, List<Role> roles, boolean enabled,
                       boolean pendingInvite, Instant lastLoginAt, Instant createdAt, Long version) {

    static UserView from(User user) {
        return new UserView(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getRoles().stream().sorted(Comparator.naturalOrder()).toList(), user.isEnabled(),
                !user.hasPassword(), user.getLastLoginAt(), user.getCreatedAt(), user.getVersion());
    }
}
