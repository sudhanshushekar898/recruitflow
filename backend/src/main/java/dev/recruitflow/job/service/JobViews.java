package dev.recruitflow.job.service;

import dev.recruitflow.job.domain.EmploymentType;
import dev.recruitflow.job.domain.JobStatus;
import dev.recruitflow.job.domain.SkillImportance;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read models returned by the job services. Entities never leave the service layer. */
public final class JobViews {

    private JobViews() {}

    public record ClientView(UUID id, String name, String industry, String contactName, String contactEmail,
                             boolean active, Instant createdAt, Long version) {
    }

    public record JobSkillView(UUID skillId, String name, String displayName, SkillImportance importance,
                               Integer minYears, Integer weight) {
    }

    /** canEdit tells the UI whether the signed-in user may change this job; the server enforces it regardless. */
    public record JobView(UUID id, UUID clientId, String clientName, String title, String description, String location,
                          EmploymentType employmentType, BigDecimal salaryMin, BigDecimal salaryMax, String currency,
                          int openings, JobStatus status, List<JobStatus> allowedStatuses, UUID ownerId,
                          String ownerName, Instant openedAt, Instant closedAt, List<JobSkillView> skills,
                          boolean canEdit, Instant createdAt, Long version) {
    }

    public record JobSummary(UUID id, String title, UUID clientId, String clientName, String location,
                             EmploymentType employmentType, JobStatus status, int openings, String ownerName,
                             boolean canEdit, Instant createdAt) {
    }
}
