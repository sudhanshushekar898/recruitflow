package dev.recruitflow.identity;

import dev.recruitflow.identity.domain.User;
import dev.recruitflow.identity.repository.UserRepository;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Public facade: lets other modules show who someone is without touching identity's entities. */
@Component
public class UserLookup {

    private final UserRepository users;

    UserLookup(UserRepository users) {
        this.users = users;
    }

    /** Full names by user id in one query; unknown ids are simply absent from the result. */
    @Transactional(readOnly = true)
    public Map<UUID, String> displayNames(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::getFullName, (a, b) -> a, java.util.HashMap::new));
    }

    @Transactional(readOnly = true)
    public boolean exists(UUID id) {
        return users.existsById(id);
    }

    /** Enabled users that may sit on an interview panel, with their names, for the scheduling form. */
    public record Interviewer(UUID id, String name) {
    }

    @Transactional(readOnly = true)
    public java.util.List<Interviewer> interviewers() {
        return users.findAll().stream()
                .filter(u -> u.isEnabled() && u.hasPassword() && u.getRoles().contains(dev.recruitflow.identity.domain.Role.INTERVIEWER))
                .sorted(java.util.Comparator.comparing(User::getFullName))
                .map(u -> new Interviewer(u.getId(), u.getFullName()))
                .toList();
    }

    /** True if every id belongs to an enabled user. */
    @Transactional(readOnly = true)
    public boolean allEnabled(Collection<UUID> ids) {
        return users.findAllById(ids).stream().filter(User::isEnabled).count() == ids.size();
    }
}
