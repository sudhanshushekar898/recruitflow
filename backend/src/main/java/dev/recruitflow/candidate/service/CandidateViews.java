package dev.recruitflow.candidate.service;

import dev.recruitflow.candidate.domain.CandidateSource;
import dev.recruitflow.candidate.domain.CandidateStatus;
import dev.recruitflow.candidate.domain.LegalBasis;
import dev.recruitflow.candidate.domain.SkillLevel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read models returned by {@link CandidateService}. Entities never leave the service layer. */
public final class CandidateViews {

    private CandidateViews() {}

    public record SkillEntryView(UUID skillId, String name, String displayName, Integer years, SkillLevel level) {
    }

    public record ConsentView(LegalBasis legalBasis, Instant givenAt, Instant expiresAt, String evidence, Instant withdrawnAt) {
    }

    public record CandidateView(UUID id, String firstName, String lastName, String email, String phone,
                                String currentTitle, String currentCompany, String location, Integer yearsExperience,
                                CandidateSource source, String summary, String linkedinUrl, CandidateStatus status,
                                UUID ownerId, String ownerName, Instant retentionUntil, List<SkillEntryView> skills,
                                ConsentView consent, Instant createdAt, Instant updatedAt, Long version) {
    }

    public record CandidateSummary(UUID id, String fullName, String email, String currentTitle, String location,
                                   Integer yearsExperience, CandidateStatus status, List<String> skills,
                                   String ownerName, Instant createdAt) {
    }

    public record NoteView(UUID id, UUID authorId, String authorName, String body, Instant createdAt) {
    }
}
