package dev.recruitflow.pipeline;

import java.util.UUID;

/** What other modules may know about an application. */
public record ApplicationRef(UUID id, UUID candidateId, UUID jobId, String status, UUID ownerId) {
}
