package dev.recruitflow.job.domain;

import java.util.Set;

/** Job lifecycle. A closed job can be reopened; everything else follows the table below. */
public enum JobStatus {
    DRAFT,
    OPEN,
    ON_HOLD,
    CLOSED;

    public Set<JobStatus> allowedNext() {
        return switch (this) {
            case DRAFT -> Set.of(OPEN, CLOSED);
            case OPEN -> Set.of(ON_HOLD, CLOSED);
            case ON_HOLD -> Set.of(OPEN, CLOSED);
            case CLOSED -> Set.of(OPEN);
        };
    }

    public boolean canMoveTo(JobStatus target) {
        return allowedNext().contains(target);
    }
}
