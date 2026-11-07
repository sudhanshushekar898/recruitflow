package dev.recruitflow.interview.domain;

import dev.recruitflow.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "interview_feedback")
public class InterviewFeedback extends BaseEntity {

    @Column(nullable = false)
    private UUID interviewId;

    @Column(nullable = false)
    private UUID interviewerId;

    @Column(nullable = false)
    private int rating;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Recommendation recommendation;

    @Column(columnDefinition = "text")
    private String strengths;

    @Column(columnDefinition = "text")
    private String concerns;

    @Column(nullable = false)
    private Instant submittedAt;

    protected InterviewFeedback() {}

    public InterviewFeedback(UUID interviewId, UUID interviewerId, int rating, Recommendation recommendation,
                             String strengths, String concerns, Instant submittedAt) {
        this.interviewId = interviewId;
        this.interviewerId = interviewerId;
        this.rating = rating;
        this.recommendation = recommendation;
        this.strengths = strengths == null || strengths.isBlank() ? null : strengths.trim();
        this.concerns = concerns == null || concerns.isBlank() ? null : concerns.trim();
        this.submittedAt = submittedAt;
    }

    public UUID getInterviewId() {
        return interviewId;
    }

    public UUID getInterviewerId() {
        return interviewerId;
    }

    public int getRating() {
        return rating;
    }

    public Recommendation getRecommendation() {
        return recommendation;
    }

    public String getStrengths() {
        return strengths;
    }

    public String getConcerns() {
        return concerns;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
