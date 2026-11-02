package dev.recruitflow.identity.service;

import dev.recruitflow.audit.AuditRecorder;
import dev.recruitflow.identity.CurrentUser;
import dev.recruitflow.identity.config.AppProperties;
import dev.recruitflow.identity.config.SecurityProperties;
import dev.recruitflow.identity.domain.Role;
import dev.recruitflow.identity.domain.TokenPurpose;
import dev.recruitflow.identity.domain.User;
import dev.recruitflow.identity.repository.RefreshTokenRepository;
import dev.recruitflow.identity.repository.UserRepository;
import dev.recruitflow.shared.error.DomainException;
import dev.recruitflow.shared.error.ErrorCode;
import dev.recruitflow.shared.paging.Likes;
import dev.recruitflow.shared.paging.PageRequests;
import dev.recruitflow.shared.paging.PageResponse;
import dev.recruitflow.shared.persistence.AfterCommit;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin-only user management. Every method is role-guarded here as well as at the route (defence in depth). */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class UserService {

    static final Set<String> SORT_FIELDS = Set.of("email", "firstName", "lastName", "createdAt", "lastLoginAt");

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final OneTimeTokenService oneTimeTokens;
    private final UserMailer mailer;
    private final AuditRecorder audit;
    private final CurrentUser currentUser;
    private final SecurityProperties properties;
    private final AppProperties app;
    private final Clock clock;

    UserService(UserRepository users, RefreshTokenRepository refreshTokens, OneTimeTokenService oneTimeTokens,
                UserMailer mailer, AuditRecorder audit, CurrentUser currentUser, SecurityProperties properties,
                AppProperties app, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.oneTimeTokens = oneTimeTokens;
        this.mailer = mailer;
        this.audit = audit;
        this.currentUser = currentUser;
        this.properties = properties;
        this.app = app;
        this.clock = clock;
    }

    @Transactional
    public UserView create(String email, String firstName, String lastName, Set<Role> roles) {
        String normalized = Emails.normalize(email);
        if (users.existsByEmail(normalized)) {
            throw duplicate();
        }
        User user;
        try {
            user = users.saveAndFlush(User.invited(normalized, firstName.trim(), lastName.trim(), roles));
        } catch (DataIntegrityViolationException e) { // lost a race with a concurrent create
            throw duplicate();
        }
        String raw = oneTimeTokens.issue(user.getId(), TokenPurpose.INVITE, properties.inviteTtl());
        String link = app.baseUrl() + "/accept-invite?token=" + raw;
        AfterCommit.run(() -> mailer.sendInvite(user.getId(), user.getEmail(), user.getFirstName(), link));
        audit.record("USER_CREATED", currentUser.id(), "USER", user.getId(), Map.of("roles", roleNames(roles)));
        return UserView.from(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserView> list(String q, Role role, Boolean enabled, int page, int size, String sort) {
        Pageable pageable = PageRequests.of(page, size, sort, SORT_FIELDS, "createdAt,desc");
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String pattern = Likes.contains(q.trim());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("email")), pattern, Likes.ESCAPE),
                        cb.like(cb.lower(root.get("firstName")), pattern, Likes.ESCAPE),
                        cb.like(cb.lower(root.get("lastName")), pattern, Likes.ESCAPE)));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (role != null) {
                predicates.add(cb.isMember(role, root.<Set<Role>>get("roles")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<User> result = users.findAll(spec, pageable);
        return PageResponse.of(result, UserView::from);
    }

    @Transactional(readOnly = true)
    public UserView get(UUID id) {
        return UserView.from(find(id));
    }

    @Transactional
    public UserView updateProfile(UUID id, String firstName, String lastName, Long version) {
        User user = find(id);
        user.checkVersion(version);
        user.updateProfile(firstName == null ? null : firstName.trim(), lastName == null ? null : lastName.trim());
        users.saveAndFlush(user);
        audit.record("USER_UPDATED", currentUser.id(), "USER", id, Map.of("fields", List.of("firstName", "lastName")));
        return UserView.from(user);
    }

    @Transactional
    public UserView replaceRoles(UUID id, Set<Role> roles, Long version) {
        User user = find(id);
        user.checkVersion(version);
        if (id.equals(currentUser.id()) && !roles.contains(Role.ADMIN)) {
            throw new DomainException(ErrorCode.INVALID_OPERATION, "You cannot remove your own admin role.");
        }
        user.replaceRoles(roles);
        users.saveAndFlush(user);
        // Access tokens keep their old roles until they expire (15 minutes); end the sessions so it is not extended.
        refreshTokens.revokeAllForUser(id, clock.instant());
        audit.record("USER_ROLES_CHANGED", currentUser.id(), "USER", id, Map.of("roles", roleNames(roles)));
        return UserView.from(find(id));
    }

    @Transactional
    public UserView deactivate(UUID id) {
        if (id.equals(currentUser.id())) {
            throw new DomainException(ErrorCode.INVALID_OPERATION, "You cannot deactivate your own account.");
        }
        User user = find(id);
        user.setEnabled(false);
        users.saveAndFlush(user);
        refreshTokens.revokeAllForUser(id, clock.instant());
        audit.record("USER_DEACTIVATED", currentUser.id(), "USER", id);
        return UserView.from(find(id));
    }

    @Transactional
    public UserView activate(UUID id) {
        User user = find(id);
        user.setEnabled(true);
        users.saveAndFlush(user);
        audit.record("USER_ACTIVATED", currentUser.id(), "USER", id);
        return UserView.from(user);
    }

    private User find(UUID id) {
        return users.findById(id)
                .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND, "User not found."));
    }

    private static List<String> roleNames(Set<Role> roles) {
        return roles.stream().map(Role::name).sorted().toList();
    }

    private static DomainException duplicate() {
        return new DomainException(ErrorCode.DUPLICATE_USER, "A user with this email already exists.");
    }
}
