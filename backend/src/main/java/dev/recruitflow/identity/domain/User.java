package dev.recruitflow.identity.domain;

import dev.recruitflow.shared.persistence.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, length = 254)
    private String email;

    @Column(length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    private boolean enabled = true;

    private boolean mustChangePassword;

    private int failedLoginCount;

    private Instant lockedUntil;

    private Instant lastLoginAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    @BatchSize(size = 50)
    private Set<Role> roles = new HashSet<>();

    protected User() {}

    private User(String email, String firstName, String lastName, Set<Role> roles) {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.roles.addAll(roles);
    }

    /** A user created by an admin: cannot log in until the invite is accepted and a password is set. */
    public static User invited(String email, String firstName, String lastName, Set<Role> roles) {
        return new User(email, firstName, lastName, roles);
    }

    /** First admin, created from configuration; must pick a new password on first login. */
    public static User bootstrapAdmin(String email, String passwordHash) {
        User user = new User(email, "System", "Administrator", Set.of(Role.ADMIN));
        user.passwordHash = passwordHash;
        user.mustChangePassword = true;
        return user;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Returns true when this failure triggered a lock. */
    public boolean recordFailedLogin(Instant now, int maxAttempts, Duration lockDuration) {
        if (lockedUntil != null && !lockedUntil.isAfter(now)) {
            // a previous lock expired: start counting again
            failedLoginCount = 0;
            lockedUntil = null;
        }
        failedLoginCount++;
        if (failedLoginCount >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
            failedLoginCount = 0;
            return true;
        }
        return false;
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLoginCount = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public void setPassword(String newHash) {
        this.passwordHash = newHash;
        this.mustChangePassword = false;
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }

    public void updateProfile(String firstName, String lastName) {
        if (firstName != null) {
            this.firstName = firstName;
        }
        if (lastName != null) {
            this.lastName = lastName;
        }
    }

    public void replaceRoles(Set<Role> newRoles) {
        roles.retainAll(newRoles);
        roles.addAll(newRoles);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }
}
