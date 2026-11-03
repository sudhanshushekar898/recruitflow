package dev.recruitflow.job.service;

import dev.recruitflow.identity.CurrentUser;
import dev.recruitflow.job.domain.Job;
import dev.recruitflow.shared.error.DomainException;
import dev.recruitflow.shared.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Object-level rule for jobs: everyone with access can read; only the owning recruiter or an admin can change
 * a job. {@code recruitflow.access.collaborative-writes=true} lets every recruiter edit every job.
 */
@Component
class JobAccessPolicy {

    private final CurrentUser currentUser;
    private final boolean collaborative;

    JobAccessPolicy(CurrentUser currentUser, @Value("${recruitflow.access.collaborative-writes:false}") boolean collaborative) {
        this.currentUser = currentUser;
        this.collaborative = collaborative;
    }

    boolean canEdit(Job job) {
        return collaborative || currentUser.hasRole("ADMIN") || job.getOwnerId().equals(currentUser.id());
    }

    void requireCanEdit(Job job) {
        if (!canEdit(job)) {
            throw new DomainException(ErrorCode.FORBIDDEN, "Only the recruiter who owns this job or an admin can change it.");
        }
    }
}
