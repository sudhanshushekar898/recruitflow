package dev.recruitflow.candidate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** The lawful basis for holding a candidate's data (at most one record per candidate). */
@Entity
@Table(name = "candidate_consents")
@EntityListeners(AuditingEntityListener.class)
public class CandidateConsent {

    @Id
    private UUID candidateId;

    @Version
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LegalBasis legalBasis;

    @Column(nullable = false)
    private Instant givenAt;

    private Instant expiresAt;

    @Column(length = 300)
    private String evidence;

    private Instant withdrawnAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    protected CandidateConsent() {}

    public CandidateConsent(UUID candidateId) {
        this.candidateId = candidateId;
    }

    public void record(LegalBasis legalBasis, Instant givenAt, Instant expiresAt, String evidence, Instant withdrawnAt) {
        this.legalBasis = legalBasis;
        this.givenAt = givenAt;
        this.expiresAt = expiresAt;
        this.evidence = evidence == null || evidence.isBlank() ? null : evidence.trim();
        this.withdrawnAt = withdrawnAt;
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public LegalBasis getLegalBasis() {
        return legalBasis;
    }

    public Instant getGivenAt() {
        return givenAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getEvidence() {
        return evidence;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }
}
