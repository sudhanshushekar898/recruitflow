package dev.recruitflow.pipeline.domain;

import java.util.Set;

/**
 * The hiring pipeline. The transition table is the single source of truth; the API reports
 * {@code allowedNext} to the UI so the rules are never duplicated in the browser.
 *
 * NEW → SCREENING → SHORTLISTED → INTERVIEW → OFFER → HIRED, with REJECTED or WITHDRAWN possible from any
 * open stage and a rejected application able to return to NEW.
 */
public enum ApplicationStatus {
    NEW,
    SCREENING,
    SHORTLISTED,
    INTERVIEW,
    OFFER,
    HIRED,
    REJECTED,
    WITHDRAWN;

    public Set<ApplicationStatus> allowedNext() {
        return switch (this) {
            case NEW -> Set.of(SCREENING, SHORTLISTED, REJECTED, WITHDRAWN);
            case SCREENING -> Set.of(SHORTLISTED, REJECTED, WITHDRAWN);
            case SHORTLISTED -> Set.of(INTERVIEW, REJECTED, WITHDRAWN);
            case INTERVIEW -> Set.of(OFFER, REJECTED, WITHDRAWN);
            case OFFER -> Set.of(HIRED, REJECTED, WITHDRAWN);
            case REJECTED -> Set.of(NEW);
            case HIRED, WITHDRAWN -> Set.of();
        };
    }

    public boolean canMoveTo(ApplicationStatus target) {
        return allowedNext().contains(target);
    }

    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }
}
