package dev.recruitflow.interview.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.UUID;

@Embeddable
public record InterviewParticipant(UUID userId, @Enumerated(EnumType.STRING) ParticipantRole role) {
}
