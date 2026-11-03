package dev.recruitflow.pipeline.api;

import dev.recruitflow.pipeline.domain.ApplicationSource;
import dev.recruitflow.pipeline.domain.ApplicationStatus;
import dev.recruitflow.pipeline.domain.RejectionReason;
import dev.recruitflow.pipeline.service.ApplicationService;
import dev.recruitflow.pipeline.service.ApplicationService.Filters;
import dev.recruitflow.pipeline.service.PipelineViews.ApplicationView;
import dev.recruitflow.pipeline.service.PipelineViews.HistoryView;
import dev.recruitflow.pipeline.service.PipelineViews.PipelineView;
import dev.recruitflow.pipeline.service.PipelineViews.StatusChangeResult;
import dev.recruitflow.shared.paging.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class ApplicationController {

    private final ApplicationService applications;

    ApplicationController(ApplicationService applications) {
        this.applications = applications;
    }

    record CreateApplicationRequest(@NotNull UUID candidateId, @NotNull UUID jobId, ApplicationSource source) {
    }

    /** reason is required (and only used) when moving to REJECTED. version guards against stale boards. */
    record ChangeStatusRequest(@NotNull ApplicationStatus toStatus, RejectionReason reason,
                               @Size(max = 1000) String comment, @NotNull Long version) {
    }

    @GetMapping("/applications")
    PageResponse<ApplicationView> list(@RequestParam(required = false) UUID jobId,
                                       @RequestParam(required = false) UUID candidateId,
                                       @RequestParam(required = false) ApplicationStatus status,
                                       @RequestParam(required = false) UUID ownerId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @RequestParam(required = false) String sort) {
        return applications.list(new Filters(jobId, candidateId, status, ownerId), page, size, sort);
    }

    @PostMapping("/applications")
    ResponseEntity<ApplicationView> create(@Valid @RequestBody CreateApplicationRequest r) {
        ApplicationView created = applications.create(r.candidateId(), r.jobId(), r.source());
        return ResponseEntity.created(URI.create("/api/v1/applications/" + created.id())).body(created);
    }

    @GetMapping("/applications/{id}")
    ApplicationView get(@PathVariable UUID id) {
        return applications.get(id);
    }

    @PostMapping("/applications/{id}/status")
    StatusChangeResult changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest r) {
        return applications.changeStatus(id, r.toStatus(), r.reason(), r.comment(), r.version());
    }

    @GetMapping("/applications/{id}/history")
    List<HistoryView> history(@PathVariable UUID id) {
        return applications.history(id);
    }

    @GetMapping("/jobs/{jobId}/pipeline")
    PipelineView pipeline(@PathVariable UUID jobId) {
        return applications.pipeline(jobId);
    }

    @GetMapping("/candidates/{candidateId}/applications")
    List<ApplicationView> forCandidate(@PathVariable UUID candidateId) {
        return applications.forCandidate(candidateId);
    }
}
