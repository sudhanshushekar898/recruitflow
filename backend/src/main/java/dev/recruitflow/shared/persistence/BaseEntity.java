package dev.recruitflow.shared.persistence;

import dev.recruitflow.shared.error.DomainException;
import dev.recruitflow.shared.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Id, optimistic-lock version and timestamps shared by mutable entities. */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id = Ids.newId();

    /** Wrapper type on purpose: null marks a new entity so Spring Data persists instead of merging. */
    @Version
    private Long version;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Rejects the change when the client edited a stale copy. */
    public void checkVersion(Long expected) {
        if (expected == null || !expected.equals(version)) {
            throw new DomainException(ErrorCode.STALE_VERSION, "The resource was modified by someone else. Reload and retry.");
        }
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof BaseEntity other && id.equals(other.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
