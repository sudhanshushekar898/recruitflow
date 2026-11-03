package dev.recruitflow.pipeline.service;

import dev.recruitflow.pipeline.domain.ApplicationSource;
import dev.recruitflow.pipeline.domain.ApplicationStatus;
import dev.recruitflow.pipeline.domain.RejectionReason;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read models returned by {@link ApplicationService}. */
public final class PipelineViews {

    private PipelineViews() {}

    /** allowedNext lists only the moves the signed-in user may actually make. */
    public record ApplicationView(UUID id, UUID candidateId, String candidateName, String candidateEmail,
                                  String candidateTitle, UUID jobId, String jobTitle, String clientName,
                                  ApplicationStatus status, List<ApplicationStatus> allowedNext,
                                  RejectionReason rejectionReason, ApplicationSource source, UUID ownerId,
                                  String ownerName, Instant appliedAt, Instant statusChangedAt, Long version) {
    }

    public record StatusChangeResult(ApplicationView application, boolean jobCloseSuggested) {
    }

    public record StageView(ApplicationStatus status, long count, List<ApplicationView> applications) {
    }

    public record PipelineView(UUID jobId, String jobTitle, String clientName, String jobStatus, int openings,
                               long hired, List<StageView> stages) {
    }

    public record HistoryView(UUID id, ApplicationStatus fromStatus, ApplicationStatus toStatus, String changedByName,
                              Instant changedAt, String comment) {
    }
}
