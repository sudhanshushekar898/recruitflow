package dev.recruitflow.pipeline.domain;

import dev.recruitflow.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A candidate's application to one job, and where it stands in the pipeline. */
@Entity
@Table(name = "applications")
public class Application extends BaseEntity {

    @Column(nullable = false)
    private UUID candidateId;

    @Column(nullable = false)
    private UUID jobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private RejectionReason rejectionReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationSource source;

    @Column(nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private Instant appliedAt;

    @Column(nullable = false)
    private Instant statusChangedAt;

    protected Application() {}

    public Application(UUID candidateId, UUID jobId, ApplicationSource source, UUID ownerId, Instant now) {
        this.candidateId = candidateId;
        this.jobId = jobId;
        this.source = source;
        this.ownerId = ownerId;
        this.appliedAt = now;
        this.statusChangedAt = now;
    }

    /** Callers validate the transition first (TransitionPolicy); this only records the result. */
    public void moveTo(ApplicationStatus target, RejectionReason reason, Instant now) {
        this.status = target;
        this.rejectionReason = target == ApplicationStatus.REJECTED ? reason : null;
        this.statusChangedAt = now;
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public UUID getJobId() {
        return jobId;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public RejectionReason getRejectionReason() {
        return rejectionReason;
    }

    public ApplicationSource getSource() {
        return source;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }
}
