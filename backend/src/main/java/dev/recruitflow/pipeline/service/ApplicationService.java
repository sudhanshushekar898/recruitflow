package dev.recruitflow.pipeline.service;

import dev.recruitflow.audit.AuditRecorder;
import dev.recruitflow.candidate.CandidateLookup;
import dev.recruitflow.candidate.CandidateRef;
import dev.recruitflow.identity.CurrentUser;
import dev.recruitflow.identity.UserLookup;
import dev.recruitflow.job.JobLookup;
import dev.recruitflow.job.JobRef;
import dev.recruitflow.pipeline.domain.Application;
import dev.recruitflow.pipeline.domain.ApplicationSource;
import dev.recruitflow.pipeline.domain.ApplicationStatus;
import dev.recruitflow.pipeline.domain.RejectionReason;
import dev.recruitflow.pipeline.domain.StatusHistory;
import dev.recruitflow.pipeline.domain.TransitionPolicy;
import dev.recruitflow.pipeline.repository.ApplicationRepository;
import dev.recruitflow.pipeline.repository.StatusHistoryRepository;
import dev.recruitflow.pipeline.service.PipelineViews.ApplicationView;
import dev.recruitflow.pipeline.service.PipelineViews.HistoryView;
import dev.recruitflow.pipeline.service.PipelineViews.PipelineView;
import dev.recruitflow.pipeline.service.PipelineViews.StageView;
import dev.recruitflow.pipeline.service.PipelineViews.StatusChangeResult;
import dev.recruitflow.shared.error.DomainException;
import dev.recruitflow.shared.error.ErrorCode;
import dev.recruitflow.shared.paging.PageRequests;
import dev.recruitflow.shared.paging.PageResponse;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Applications and the hiring pipeline: who applied where, where they stand, and the full history of moves. */
@Service
@PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
public class ApplicationService {

    static final Set<String> SORT_FIELDS = Set.of("appliedAt", "statusChangedAt");

    public record Filters(UUID jobId, UUID candidateId, ApplicationStatus status, UUID ownerId) {
    }

    private final ApplicationRepository applications;
    private final StatusHistoryRepository history;
    private final CandidateLookup candidateLookup;
    private final JobLookup jobLookup;
    private final UserLookup userLookup;
    private final CurrentUser currentUser;
    private final AuditRecorder audit;
    private final Clock clock;
    private final TransactionTemplate tx;

    ApplicationService(ApplicationRepository applications, StatusHistoryRepository history, CandidateLookup candidateLookup,
                       JobLookup jobLookup, UserLookup userLookup, CurrentUser currentUser, AuditRecorder audit, Clock clock,
                       PlatformTransactionManager transactionManager) {
        this.applications = applications;
        this.history = history;
        this.candidateLookup = candidateLookup;
        this.jobLookup = jobLookup;
        this.userLookup = userLookup;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * Not transactional itself: a concurrent duplicate aborts the loser's transaction in PostgreSQL, so the
     * existing application is looked up only after it has rolled back (the unique constraint decides the race).
     */
    public ApplicationView create(UUID candidateId, UUID jobId, ApplicationSource source) {
        try {
            return tx.execute(status -> createInTransaction(candidateId, jobId, source));
        } catch (DataIntegrityViolationException e) {
            throw applications.findByCandidateIdAndJobId(candidateId, jobId)
                    .map(existing -> duplicate(existing.getId()))
                    .orElseThrow(() -> e);
        }
    }

    private ApplicationView createInTransaction(UUID candidateId, UUID jobId, ApplicationSource source) {
        CandidateRef candidate = candidateLookup.find(candidateId)
                .orElseThrow(() -> fieldError("candidateId", "candidate does not exist"));
        JobRef job = jobLookup.find(jobId).orElseThrow(() -> fieldError("jobId", "job does not exist"));
        if (!candidate.isActive()) {
            throw new DomainException(ErrorCode.INVALID_OPERATION,
                    "Only active candidates can be added to a job; this candidate is " + candidate.status().toLowerCase().replace('_', ' ') + ".");
        }
        if (!job.isOpen()) {
            throw new DomainException(ErrorCode.JOB_NOT_OPEN, "Candidates can only be added to open jobs.");
        }
        applications.findByCandidateIdAndJobId(candidateId, jobId).ifPresent(existing -> {
            throw duplicate(existing.getId());
        });
        Instant now = clock.instant();
        Application application = applications.saveAndFlush(new Application(candidateId, jobId,
                source == null ? ApplicationSource.OTHER : source, currentUser.id(), now));
        history.save(new StatusHistory(application.getId(), null, ApplicationStatus.NEW, currentUser.id(), now, null));
        audit.record("APPLICATION_CREATED", currentUser.id(), "APPLICATION", application.getId(),
                Map.of("jobId", jobId.toString(), "candidateId", candidateId.toString()));
        return toViews(List.of(application)).get(0);
    }

    @Transactional
    public StatusChangeResult changeStatus(UUID id, ApplicationStatus to, RejectionReason reason, String comment, Long version) {
        Application application = find(id);
        application.checkVersion(version);
        ApplicationStatus from = application.getStatus();
        if (!TransitionPolicy.isLegal(from, to)) {
            throw new DomainException(ErrorCode.INVALID_TRANSITION, "An application cannot move from " + from + " to " + to + ".",
                    Map.of("from", from.name(), "to", to.name(),
                            "allowed", from.allowedNext().stream().map(Enum::name).sorted().toList()));
        }
        if (TransitionPolicy.requiresReason(to) && reason == null) {
            throw fieldError("reason", "a rejection reason is required");
        }
        if (TransitionPolicy.needsOwnerOrAdmin(from, to) && !isOwnerOrAdmin(application)) {
            throw new DomainException(ErrorCode.FORBIDDEN,
                    "Only the recruiter who owns this application or an admin can move it to " + to + ".");
        }
        Instant now = clock.instant();
        application.moveTo(to, reason, now);
        applications.saveAndFlush(application);
        history.save(new StatusHistory(id, from, to, currentUser.id(), now, comment));
        Map<String, Object> details = new HashMap<>(Map.of("from", from.name(), "to", to.name()));
        if (reason != null && to == ApplicationStatus.REJECTED) {
            details.put("reason", reason.name());
        }
        audit.record("APPLICATION_STATUS_CHANGED", currentUser.id(), "APPLICATION", id, details);

        boolean closeSuggested = false;
        if (to == ApplicationStatus.HIRED) {
            long hired = applications.countByJobIdAndStatus(application.getJobId(), ApplicationStatus.HIRED);
            closeSuggested = jobLookup.find(application.getJobId()).map(j -> j.isOpen() && hired >= j.openings()).orElse(false);
        }
        return new StatusChangeResult(toViews(List.of(application)).get(0), closeSuggested);
    }

    @Transactional(readOnly = true)
    public ApplicationView get(UUID id) {
        return toViews(List.of(find(id))).get(0);
    }

    @Transactional(readOnly = true)
    public List<HistoryView> history(UUID id) {
        find(id);
        List<StatusHistory> rows = history.findByApplicationIdOrderByChangedAtAscIdAsc(id);
        Map<UUID, String> names = userLookup.displayNames(rows.stream().map(StatusHistory::getChangedBy)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        return rows.stream().map(h -> new HistoryView(h.getId(), h.getFromStatus(), h.getToStatus(),
                h.getChangedBy() == null ? "System" : names.get(h.getChangedBy()), h.getChangedAt(), h.getComment())).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationView> list(Filters f, int page, int size, String sort) {
        Pageable pageable = PageRequests.of(page, size, sort, SORT_FIELDS, "statusChangedAt,desc");
        Specification<Application> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f.jobId() != null) {
                predicates.add(cb.equal(root.get("jobId"), f.jobId()));
            }
            if (f.candidateId() != null) {
                predicates.add(cb.equal(root.get("candidateId"), f.candidateId()));
            }
            if (f.status() != null) {
                predicates.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.ownerId() != null) {
                predicates.add(cb.equal(root.get("ownerId"), f.ownerId()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var result = applications.findAll(spec, pageable);
        List<ApplicationView> views = toViews(result.getContent());
        return new PageResponse<>(views, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /** Every application of one job, grouped by stage in pipeline order, for the board view. */
    @Transactional(readOnly = true)
    public PipelineView pipeline(UUID jobId) {
        JobRef job = jobLookup.find(jobId).orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND, "Job not found."));
        List<ApplicationView> views = toViews(applications.findByJobIdOrderByStatusChangedAtDesc(jobId));
        Map<ApplicationStatus, List<ApplicationView>> byStage = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus s : ApplicationStatus.values()) {
            byStage.put(s, new ArrayList<>());
        }
        views.forEach(v -> byStage.get(v.status()).add(v));
        List<StageView> stages = byStage.entrySet().stream()
                .map(e -> new StageView(e.getKey(), e.getValue().size(), e.getValue())).toList();
        return new PipelineView(job.id(), job.title(), job.clientName(), job.status(), job.openings(),
                byStage.get(ApplicationStatus.HIRED).size(), stages);
    }

    @Transactional(readOnly = true)
    public List<ApplicationView> forCandidate(UUID candidateId) {
        candidateLookup.find(candidateId).orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND, "Candidate not found."));
        return toViews(applications.findByCandidateIdOrderByAppliedAtDesc(candidateId));
    }

    private Application find(UUID id) {
        return applications.findById(id).orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND, "Application not found."));
    }

    private boolean isOwnerOrAdmin(Application a) {
        return currentUser.hasRole("ADMIN") || a.getOwnerId().equals(currentUser.id());
    }

    /** Resolves names for a batch of applications with one lookup per related module, never one per row. */
    private List<ApplicationView> toViews(Collection<Application> list) {
        Map<UUID, CandidateRef> candidates = candidateLookup.findByIds(list.stream().map(Application::getCandidateId).collect(Collectors.toSet()));
        Map<UUID, JobRef> jobs = jobLookup.findByIds(list.stream().map(Application::getJobId).collect(Collectors.toSet()));
        Set<UUID> ownerIds = new HashSet<>();
        list.forEach(a -> ownerIds.add(a.getOwnerId()));
        Map<UUID, String> owners = userLookup.displayNames(ownerIds);
        return list.stream().map(a -> {
            CandidateRef c = candidates.get(a.getCandidateId());
            JobRef j = jobs.get(a.getJobId());
            boolean privileged = isOwnerOrAdmin(a);
            List<ApplicationStatus> allowed = a.getStatus().allowedNext().stream()
                    .filter(to -> privileged || !TransitionPolicy.needsOwnerOrAdmin(a.getStatus(), to))
                    .sorted().toList();
            return new ApplicationView(a.getId(), a.getCandidateId(), c == null ? "Unknown candidate" : c.fullName(),
                    c == null ? null : c.email(), c == null ? null : c.currentTitle(), a.getJobId(),
                    j == null ? "Unknown job" : j.title(), j == null ? null : j.clientName(), a.getStatus(), allowed,
                    a.getRejectionReason(), a.getSource(), a.getOwnerId(), owners.get(a.getOwnerId()), a.getAppliedAt(),
                    a.getStatusChangedAt(), a.getVersion());
        }).toList();
    }

    private static DomainException duplicate(UUID existingId) {
        return new DomainException(ErrorCode.DUPLICATE_APPLICATION, "This candidate has already applied to this job.",
                Map.of("existingApplicationId", existingId.toString()));
    }

    private static DomainException fieldError(String field, String message) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.",
                Map.of("errors", List.of(Map.of("field", field, "message", message))));
    }
}
