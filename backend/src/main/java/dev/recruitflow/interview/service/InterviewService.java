package dev.recruitflow.interview.service;

import dev.recruitflow.audit.AuditRecorder;
import dev.recruitflow.candidate.CandidateLookup;
import dev.recruitflow.candidate.CandidateRef;
import dev.recruitflow.identity.CurrentUser;
import dev.recruitflow.identity.UserLookup;
import dev.recruitflow.interview.domain.Interview;
import dev.recruitflow.interview.domain.InterviewFeedback;
import dev.recruitflow.interview.domain.InterviewParticipant;
import dev.recruitflow.interview.domain.InterviewStatus;
import dev.recruitflow.interview.domain.InterviewType;
import dev.recruitflow.interview.domain.ParticipantRole;
import dev.recruitflow.interview.domain.Recommendation;
import dev.recruitflow.interview.repository.FeedbackRepository;
import dev.recruitflow.interview.repository.InterviewRepository;
import dev.recruitflow.interview.service.InterviewViews.FeedbackList;
import dev.recruitflow.interview.service.InterviewViews.FeedbackView;
import dev.recruitflow.interview.service.InterviewViews.InterviewView;
import dev.recruitflow.interview.service.InterviewViews.ParticipantView;
import dev.recruitflow.job.JobLookup;
import dev.recruitflow.job.JobRef;
import dev.recruitflow.pipeline.ApplicationLookup;
import dev.recruitflow.pipeline.ApplicationRef;
import dev.recruitflow.shared.error.DomainException;
import dev.recruitflow.shared.error.ErrorCode;
import dev.recruitflow.shared.paging.PageRequests;
import dev.recruitflow.shared.paging.PageResponse;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduling, clash detection and blind feedback. Recruiters and admins manage interviews; anyone on the panel
 * can read their own interviews and give feedback; nobody else can see an interview.
 */
@Service
public class InterviewService {

    static final Set<String> SORT_FIELDS = Set.of("scheduledStart", "createdAt");
    private static final Set<String> SCHEDULABLE = Set.of("SHORTLISTED", "INTERVIEW");

    public record ParticipantInput(UUID userId, ParticipantRole role) {
    }

    public record NewInterview(UUID applicationId, InterviewType type, Instant start, Instant end, String locationOrLink,
                               String notes, List<ParticipantInput> participants) {
    }

    /** Null fields stay unchanged; a participants list replaces the whole panel. */
    public record Changes(Instant start, Instant end, InterviewType type, String locationOrLink, String notes,
                          List<ParticipantInput> participants) {
    }

    public record Filters(Instant from, Instant to, InterviewStatus status, UUID interviewerId, UUID applicationId) {
    }

    public record FeedbackInput(int rating, Recommendation recommendation, String strengths, String concerns) {
    }

    private final InterviewRepository interviews;
    private final FeedbackRepository feedbackRepository;
    private final ApplicationLookup applicationLookup;
    private final CandidateLookup candidateLookup;
    private final JobLookup jobLookup;
    private final UserLookup userLookup;
    private final CurrentUser currentUser;
    private final AuditRecorder audit;
    private final Clock clock;
    private final JdbcTemplate jdbc;

    InterviewService(InterviewRepository interviews, FeedbackRepository feedbackRepository, ApplicationLookup applicationLookup,
                     CandidateLookup candidateLookup, JobLookup jobLookup, UserLookup userLookup, CurrentUser currentUser,
                     AuditRecorder audit, Clock clock, JdbcTemplate jdbc) {
        this.interviews = interviews;
        this.feedbackRepository = feedbackRepository;
        this.applicationLookup = applicationLookup;
        this.candidateLookup = candidateLookup;
        this.jobLookup = jobLookup;
        this.userLookup = userLookup;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    // ---- managing (recruiters and admins)

    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @Transactional
    public InterviewView schedule(NewInterview in) {
        validateWindow(in.start(), in.end(), true);
        ApplicationRef application = applicationLookup.find(in.applicationId())
                .orElseThrow(() -> fieldError("applicationId", "application does not exist"));
        if (!SCHEDULABLE.contains(application.status())) {
            throw new DomainException(ErrorCode.INVALID_OPERATION,
                    "Interviews can only be scheduled for shortlisted or interview-stage applications; this one is " + application.status() + ".");
        }
        Set<InterviewParticipant> panel = validatedPanel(in.participants());
        lock(application.id(), panel);
        throwOnConflicts(panel, in.start(), in.end(), new UUID(0, 0));

        Interview interview = new Interview(application.id(), interviews.maxRound(application.id()) + 1, in.type(), in.start(),
                in.end(), in.locationOrLink(), in.notes(), panel);
        interviews.saveAndFlush(interview);
        boolean advanced = applicationLookup.advanceToInterview(application.id(), currentUser.id());
        audit.record("INTERVIEW_SCHEDULED", currentUser.id(), "INTERVIEW", interview.getId(),
                Map.of("applicationId", application.id().toString(), "round", interview.getRoundNo(), "advancedApplication", advanced));
        return toViews(List.of(interview)).get(0);
    }

    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @Transactional
    public InterviewView reschedule(UUID id, Changes changes, Long version) {
        Interview interview = find(id);
        interview.checkVersion(version);
        requireScheduled(interview, "rescheduled");
        Instant start = changes.start() != null ? changes.start() : interview.getScheduledStart();
        Instant end = changes.end() != null ? changes.end() : interview.getScheduledEnd();
        validateWindow(start, end, changes.start() != null);
        Set<InterviewParticipant> panel = changes.participants() != null ? validatedPanel(changes.participants()) : interview.getParticipants();
        lock(interview.getApplicationId(), panel);
        throwOnConflicts(panel, start, end, interview.getId());

        interview.reschedule(changes.start(), changes.end(), changes.type(), changes.locationOrLink(), changes.notes());
        if (changes.participants() != null) {
            interview.replaceParticipants(panel);
        }
        interviews.saveAndFlush(interview);
        audit.record("INTERVIEW_RESCHEDULED", currentUser.id(), "INTERVIEW", id);
        return toViews(List.of(interview)).get(0);
    }

    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @Transactional
    public InterviewView cancel(UUID id) {
        return finish(id, InterviewStatus.CANCELLED, "cancelled", "INTERVIEW_CANCELLED");
    }

    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @Transactional
    public InterviewView complete(UUID id) {
        return finish(id, InterviewStatus.COMPLETED, "completed", "INTERVIEW_COMPLETED");
    }

    private InterviewView finish(UUID id, InterviewStatus target, String verb, String auditAction) {
        Interview interview = find(id);
        requireScheduled(interview, verb);
        interview.setStatus(target);
        interviews.saveAndFlush(interview);
        audit.record(auditAction, currentUser.id(), "INTERVIEW", id);
        return toViews(List.of(interview)).get(0);
    }

    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @Transactional(readOnly = true)
    public PageResponse<InterviewView> list(Filters f, int page, int size, String sort) {
        Pageable pageable = PageRequests.of(page, size, sort, SORT_FIELDS, "scheduledStart,desc");
        Specification<Interview> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledEnd"), f.from()));
            }
            if (f.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("scheduledStart"), f.to()));
            }
            if (f.status() != null) {
                predicates.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.applicationId() != null) {
                predicates.add(cb.equal(root.get("applicationId"), f.applicationId()));
            }
            if (f.interviewerId() != null) {
                Subquery<Integer> has = query.subquery(Integer.class);
                var inner = has.correlate(root);
                var member = inner.join("participants");
                has.select(cb.literal(1)).where(cb.equal(member.get("userId"), f.interviewerId()));
                predicates.add(cb.exists(has));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var result = interviews.findAll(spec, pageable);
        List<InterviewView> views = toViews(result.getContent());
        return new PageResponse<>(views, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @Transactional(readOnly = true)
    public List<UserLookup.Interviewer> interviewers() {
        return userLookup.interviewers();
    }

    // ---- everyone with a seat on the panel

    /** The signed-in user's interviews. scope: upcoming (still to happen), past, or all. */
    @Transactional(readOnly = true)
    public List<InterviewView> mine(String scope) {
        Instant now = clock.instant();
        List<Interview> mine = interviews.findByParticipant(currentUser.id()).stream().filter(i -> switch (scope == null ? "all" : scope) {
            case "upcoming" -> i.getStatus() == InterviewStatus.SCHEDULED && i.getScheduledEnd().isAfter(now);
            case "past" -> !(i.getStatus() == InterviewStatus.SCHEDULED && i.getScheduledEnd().isAfter(now));
            default -> true;
        }).toList();
        if ("upcoming".equals(scope)) {
            mine = mine.stream().sorted(Comparator.comparing(Interview::getScheduledStart)).toList();
        }
        return toViews(mine);
    }

    @Transactional(readOnly = true)
    public InterviewView get(UUID id) {
        Interview interview = find(id);
        requireCanView(interview);
        return toViews(List.of(interview)).get(0);
    }

    @Transactional
    public FeedbackView submitFeedback(UUID id, FeedbackInput in) {
        Interview interview = find(id);
        UUID me = currentUser.id();
        if (!interview.isParticipant(me)) {
            throw new DomainException(ErrorCode.FORBIDDEN, "Only people on this interview's panel can give feedback.");
        }
        if (interview.getStatus() == InterviewStatus.CANCELLED) {
            throw new DomainException(ErrorCode.INVALID_OPERATION, "This interview was cancelled.");
        }
        if (feedbackRepository.existsByInterviewIdAndInterviewerId(id, me)) {
            throw new DomainException(ErrorCode.FEEDBACK_ALREADY_SUBMITTED, "You have already given feedback for this interview.");
        }
        InterviewFeedback saved;
        try {
            saved = feedbackRepository.saveAndFlush(new InterviewFeedback(id, me, in.rating(), in.recommendation(),
                    in.strengths(), in.concerns(), clock.instant()));
        } catch (DataIntegrityViolationException e) { // a concurrent submission won the unique constraint
            throw new DomainException(ErrorCode.FEEDBACK_ALREADY_SUBMITTED, "You have already given feedback for this interview.");
        }
        audit.record("FEEDBACK_SUBMITTED", me, "INTERVIEW", id, Map.of("rating", in.rating(), "recommendation", in.recommendation().name()));
        return toFeedbackViews(List.of(saved), me).get(0);
    }

    /**
     * Blind feedback: recruiters and admins see everything; a panel member sees only their own until they have
     * submitted it, then everyone's. This keeps colleagues from anchoring on each other.
     */
    @Transactional(readOnly = true)
    public FeedbackList feedback(UUID id) {
        Interview interview = find(id);
        requireCanView(interview);
        UUID me = currentUser.id();
        List<InterviewFeedback> all = feedbackRepository.findByInterviewIdOrderBySubmittedAtAsc(id);
        boolean manager = isManager();
        boolean submittedMine = all.stream().anyMatch(f -> f.getInterviewerId().equals(me));
        if (manager || submittedMine) {
            return new FeedbackList(toFeedbackViews(all, me), false, 0);
        }
        return new FeedbackList(List.of(), true, all.size());
    }

    // ---- helpers

    private Interview find(UUID id) {
        return interviews.findById(id).orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND, "Interview not found."));
    }

    private boolean isManager() {
        return currentUser.hasRole("ADMIN") || currentUser.hasRole("RECRUITER");
    }

    private void requireCanView(Interview interview) {
        if (!isManager() && !interview.isParticipant(currentUser.id())) {
            throw new DomainException(ErrorCode.FORBIDDEN, "You are not on this interview's panel.");
        }
    }

    private static void requireScheduled(Interview interview, String verb) {
        if (interview.getStatus() != InterviewStatus.SCHEDULED) {
            throw new DomainException(ErrorCode.INVALID_OPERATION, "Only scheduled interviews can be " + verb + "; this one is "
                    + interview.getStatus().name().toLowerCase().replace('_', ' ') + ".");
        }
    }

    private void validateWindow(Instant start, Instant end, boolean mustBeFuture) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw fieldError("end", "must be after the start time");
        }
        if (mustBeFuture && !start.isAfter(clock.instant())) {
            throw fieldError("start", "must be in the future");
        }
    }

    private Set<InterviewParticipant> validatedPanel(List<ParticipantInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw fieldError("participants", "at least one interviewer is required");
        }
        Set<UUID> ids = new HashSet<>();
        Set<InterviewParticipant> panel = new LinkedHashSet<>();
        for (ParticipantInput p : inputs) {
            if (!ids.add(p.userId())) {
                throw fieldError("participants", "the same person is listed more than once");
            }
            panel.add(new InterviewParticipant(p.userId(), p.role() == null ? ParticipantRole.PANEL : p.role()));
        }
        if (!userLookup.allEnabled(ids)) {
            throw fieldError("participants", "contains someone who does not exist or is deactivated");
        }
        return panel;
    }

    /**
     * Takes a transaction-scoped advisory lock per person (and per application, so round numbers cannot collide)
     * in a fixed order. Two bookings for the same interviewer therefore run one after the other, and the second
     * sees the first when it checks for clashes: the check-then-insert race cannot double-book.
     */
    private void lock(UUID applicationId, Set<InterviewParticipant> panel) {
        List<String> keys = new ArrayList<>();
        keys.add("application:" + applicationId);
        panel.forEach(p -> keys.add("interviewer:" + p.userId()));
        keys.stream().sorted().forEach(key -> jdbc.execute((ConnectionCallback<Void>) con -> {
            try (var ps = con.prepareStatement("select pg_advisory_xact_lock(hashtext(?))")) {
                ps.setString(1, key);
                ps.execute();
            }
            return null;
        }));
    }

    private void throwOnConflicts(Set<InterviewParticipant> panel, Instant start, Instant end, UUID excludeId) {
        List<Map<String, Object>> conflicts = new ArrayList<>();
        Map<UUID, String> names = userLookup.displayNames(panel.stream().map(InterviewParticipant::userId).toList());
        for (InterviewParticipant p : panel) {
            for (Interview clash : interviews.findOverlapping(p.userId(), start, end, excludeId)) {
                Map<String, Object> c = new HashMap<>();
                c.put("userId", p.userId().toString());
                c.put("userName", names.get(p.userId()));
                c.put("interviewId", clash.getId().toString());
                c.put("start", clash.getScheduledStart().toString());
                c.put("end", clash.getScheduledEnd().toString());
                conflicts.add(c);
            }
        }
        if (!conflicts.isEmpty()) {
            throw new DomainException(ErrorCode.INTERVIEW_CONFLICT, "An interviewer already has an interview at that time.",
                    Map.of("conflicts", conflicts));
        }
    }

    private List<InterviewView> toViews(Collection<Interview> list) {
        UUID me = currentUser.id();
        boolean manager = isManager();
        Map<UUID, ApplicationRef> apps = applicationLookup.findByIds(list.stream().map(Interview::getApplicationId).collect(Collectors.toSet()));
        Map<UUID, CandidateRef> candidates = candidateLookup.findByIds(apps.values().stream().map(ApplicationRef::candidateId).collect(Collectors.toSet()));
        Map<UUID, JobRef> jobs = jobLookup.findByIds(apps.values().stream().map(ApplicationRef::jobId).collect(Collectors.toSet()));
        Set<UUID> userIds = new HashSet<>();
        list.forEach(i -> i.getParticipants().forEach(p -> userIds.add(p.userId())));
        Map<UUID, String> names = userLookup.displayNames(userIds);
        Map<UUID, Set<UUID>> submitted = new HashMap<>();
        if (!list.isEmpty()) {
            feedbackRepository.findByInterviewIdIn(list.stream().map(Interview::getId).toList())
                    .forEach(f -> submitted.computeIfAbsent(f.getInterviewId(), k -> new HashSet<>()).add(f.getInterviewerId()));
        }
        return list.stream().map(i -> {
            ApplicationRef app = apps.get(i.getApplicationId());
            CandidateRef candidate = app == null ? null : candidates.get(app.candidateId());
            JobRef job = app == null ? null : jobs.get(app.jobId());
            Set<UUID> done = submitted.getOrDefault(i.getId(), Set.of());
            List<ParticipantView> participants = i.getParticipants().stream()
                    .sorted(Comparator.comparing((InterviewParticipant p) -> p.role().ordinal()).thenComparing(p -> names.getOrDefault(p.userId(), "")))
                    .map(p -> new ParticipantView(p.userId(), names.get(p.userId()), p.role(), done.contains(p.userId()))).toList();
            boolean canGiveFeedback = i.isParticipant(me) && i.getStatus() != InterviewStatus.CANCELLED && !done.contains(me);
            return new InterviewView(i.getId(), i.getApplicationId(), manager && candidate != null ? candidate.id() : null,
                    candidate == null ? "Unknown candidate" : candidate.fullName(), candidate == null ? null : candidate.currentTitle(),
                    manager && job != null ? job.id() : null, job == null ? "Unknown job" : job.title(), job == null ? null : job.clientName(),
                    app == null ? null : app.status(), i.getRoundNo(), i.getType(), i.getScheduledStart(), i.getScheduledEnd(),
                    i.getLocationOrLink(), i.getStatus(), i.getNotes(), participants, manager, canGiveFeedback, i.getVersion());
        }).toList();
    }

    private List<FeedbackView> toFeedbackViews(Collection<InterviewFeedback> list, UUID me) {
        Map<UUID, String> names = userLookup.displayNames(list.stream().map(InterviewFeedback::getInterviewerId).collect(Collectors.toSet()));
        return list.stream().map(f -> new FeedbackView(f.getId(), f.getInterviewerId(), names.get(f.getInterviewerId()), f.getRating(),
                f.getRecommendation(), f.getStrengths(), f.getConcerns(), f.getSubmittedAt(), f.getInterviewerId().equals(me))).toList();
    }

    private static DomainException fieldError(String field, String message) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.",
                Map.of("errors", List.of(Map.of("field", field, "message", message))));
    }
}
