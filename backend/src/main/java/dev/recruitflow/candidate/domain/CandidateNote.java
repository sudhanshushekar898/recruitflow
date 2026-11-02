package dev.recruitflow.candidate.domain;

import dev.recruitflow.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "candidate_notes")
public class CandidateNote extends BaseEntity {

    @Column(nullable = false)
    private UUID candidateId;

    @Column(nullable = false)
    private UUID authorId;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    protected CandidateNote() {}

    public CandidateNote(UUID candidateId, UUID authorId, String body) {
        this.candidateId = candidateId;
        this.authorId = authorId;
        this.body = body.trim();
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getBody() {
        return body;
    }
}
