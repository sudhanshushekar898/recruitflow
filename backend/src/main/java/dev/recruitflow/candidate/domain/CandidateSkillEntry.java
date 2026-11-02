package dev.recruitflow.candidate.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.UUID;

/** A skill held by a candidate. Equality covers every field so a changed level or years is persisted. */
@Embeddable
public record CandidateSkillEntry(UUID skillId, Integer years, @Enumerated(EnumType.STRING) SkillLevel level) {
}
