package dev.recruitflow.job;

import java.util.UUID;

/** What other modules may know about a job. */
public record JobRef(UUID id, String title, String clientName, String status, int openings, UUID ownerId) {

    public boolean isOpen() {
        return "OPEN".equals(status);
    }
}
