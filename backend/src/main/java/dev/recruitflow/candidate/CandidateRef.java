package dev.recruitflow.candidate;

import java.util.UUID;

/** What other modules may know about a candidate. */
public record CandidateRef(UUID id, String fullName, String email, String currentTitle, String status) {

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
