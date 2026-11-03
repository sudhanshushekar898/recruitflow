package dev.recruitflow.pipeline.domain;

import dev.recruitflow.shared.persistence.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** One append-only step in an application's history; the source for funnel and time-in-stage analytics. */
@Entity
@Table(name = "application_status_history")
public class StatusHistory implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus toStatus;

    private UUID changedBy;

    @Column(nullable = false)
    private Instant changedAt;

    @Column(length = 1000)
    private String comment;

    @Transient
    private boolean isNew;

    protected StatusHistory() {}

    public StatusHistory(UUID applicationId, ApplicationStatus from, ApplicationStatus to, UUID changedBy, Instant changedAt, String comment) {
        this.id = Ids.newId();
        this.applicationId = applicationId;
        this.fromStatus = from;
        this.toStatus = to;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
        this.comment = comment == null || comment.isBlank() ? null : comment.trim();
        this.isNew = true;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public ApplicationStatus getFromStatus() {
        return fromStatus;
    }

    public ApplicationStatus getToStatus() {
        return toStatus;
    }

    public UUID getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public String getComment() {
        return comment;
    }
}
