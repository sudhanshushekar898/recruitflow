package dev.recruitflow.job.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.UUID;

/** A skill a job asks for. These weights also feed the skill-matching feature planned for Phase 3. */
@Embeddable
public record JobSkillEntry(UUID skillId, @Enumerated(EnumType.STRING) SkillImportance importance, Integer minYears, Integer weight) {
}
