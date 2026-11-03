package dev.recruitflow.job.service;

import dev.recruitflow.audit.AuditRecorder;
import dev.recruitflow.identity.CurrentUser;
import dev.recruitflow.identity.UserLookup;
import dev.recruitflow.job.domain.Client;
import dev.recruitflow.job.domain.EmploymentType;
import dev.recruitflow.job.domain.Job;
import dev.recruitflow.job.domain.JobSkillEntry;
import dev.recruitflow.job.domain.JobStatus;
import dev.recruitflow.job.domain.SkillImportance;
import dev.recruitflow.job.repository.ClientRepository;
import dev.recruitflow.job.repository.JobRepository;
import dev.recruitflow.job.service.JobViews.JobSkillView;
import dev.recruitflow.job.service.JobViews.JobSummary;
import dev.recruitflow.job.service.JobViews.JobView;
import dev.recruitflow.shared.error.DomainException;
import dev.recruitflow.shared.error.ErrorCode;
import dev.recruitflow.shared.paging.Likes;
import dev.recruitflow.shared.paging.PageRequests;
import dev.recruitflow.shared.paging.PageResponse;
import dev.recruitflow.skill.SkillCatalog;
import dev.recruitflow.skill.SkillRef;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Job postings and their lifecycle. Changing a job is limited to its owner or an admin (see JobAccessPolicy). */
@Service
@PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
public class JobService {

    static final Set<String> SORT_FIELDS = Set.of("title", "createdAt", "openedAt", "status");

    public record SkillInput(UUID skillId, SkillImportance importance, Integer minYears, Integer weight) {
    }

    public record NewJob(UUID clientId, String title, String description, String location, EmploymentType employmentType,
                         BigDecimal salaryMin, BigDecimal salaryMax, String currency, Integer openings, List<SkillInput> skills) {
    }

    public record JobFilters(String q, JobStatus status, UUID clientId, UUID ownerId, String location) {
    }

    private final JobRepository jobs;
    private final ClientRepository clients;
    private final SkillCatalog skillCatalog;
    private final UserLookup userLookup;
    private final CurrentUser currentUser;
    private final JobAccessPolicy policy;
    private final AuditRecorder audit;
    private final Clock clock;

    JobService(JobRepository jobs, ClientRepository clients, SkillCatalog skillCatalog, UserLookup userLookup,
               CurrentUser currentUser, JobAccessPolicy policy, AuditRecorder audit, Clock clock) {
        this.jobs = jobs;
        this.clients = clients;
        this.skillCatalog = skillCatalog;
        this.userLookup = userLookup;
        this.currentUser = currentUser;
        this.policy = policy;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public JobView create(NewJob in) {
        requireActiveClient(in.clientId());
        validateSalary(in.salaryMin(), in.salaryMax(), in.currency());
        Job job = new Job(in.clientId(), in.title(), in.description(), in.location(), in.employmentType(),
                in.salaryMin(), in.salaryMax(), in.currency(), in.openings() == null ? 1 : in.openings(), currentUser.id());
        job.replaceSkills(validatedSkills(in.skills()));
        jobs.saveAndFlush(job);
        audit.record("JOB_CREATED", currentUser.id(), "JOB", job.getId());
        return view(job);
    }

    @Transactional(readOnly = true)
    public PageResponse<JobSummary> search(JobFilters f, int page, int size, String sort) {
        Pageable pageable = PageRequests.of(page, size, sort, SORT_FIELDS, "createdAt,desc");
        Specification<Job> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f.q() != null && !f.q().isBlank()) {
                for (String token : f.q().trim().split("\\s+")) {
                    predicates.add(cb.like(root.get("searchText"), Likes.contains(token), Likes.ESCAPE));
                }
            }
            if (f.status() != null) {
                predicates.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.clientId() != null) {
                predicates.add(cb.equal(root.get("clientId"), f.clientId()));
            }
            if (f.ownerId() != null) {
                predicates.add(cb.equal(root.get("ownerId"), f.ownerId()));
            }
            if (f.location() != null && !f.location().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("location")), Likes.contains(f.location().trim()), Likes.ESCAPE));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var result = jobs.findAll(spec, pageable);
        Map<UUID, String> clientNames = clientNames(result.stream().map(Job::getClientId).collect(Collectors.toSet()));
        Map<UUID, String> owners = userLookup.displayNames(result.stream().map(Job::getOwnerId).collect(Collectors.toSet()));
        return PageResponse.of(result, j -> new JobSummary(j.getId(), j.getTitle(), j.getClientId(), clientNames.get(j.getClientId()),
                j.getLocation(), j.getEmploymentType(), j.getStatus(), j.getOpenings(), owners.get(j.getOwnerId()),
                policy.canEdit(j), j.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public JobView get(UUID id) {
        return view(find(id));
    }

    @Transactional
    public JobView update(UUID id, NewJob changes, Long version) {
        Job job = find(id);
        policy.requireCanEdit(job);
        job.checkVersion(version);
        if (changes.clientId() != null) {
            requireActiveClient(changes.clientId());
        }
        List<String> changed = job.apply(changes.title(), changes.description(), changes.location(), changes.employmentType(),
                changes.salaryMin(), changes.salaryMax(), changes.currency(), changes.openings(), changes.clientId());
        validateSalary(job.getSalaryMin(), job.getSalaryMax(), job.getCurrency());
        jobs.saveAndFlush(job);
        if (!changed.isEmpty()) {
            audit.record("JOB_UPDATED", currentUser.id(), "JOB", id, Map.of("fields", changed));
        }
        return view(job);
    }

    @Transactional
    public JobView replaceSkills(UUID id, List<SkillInput> skills) {
        Job job = find(id);
        policy.requireCanEdit(job);
        job.replaceSkills(validatedSkills(skills));
        jobs.saveAndFlush(job);
        audit.record("JOB_SKILLS_CHANGED", currentUser.id(), "JOB", id, Map.of("count", skills.size()));
        return view(job);
    }

    @Transactional
    public JobView changeStatus(UUID id, JobStatus target) {
        Job job = find(id);
        policy.requireCanEdit(job);
        JobStatus from = job.getStatus();
        if (!from.canMoveTo(target)) {
            throw new DomainException(ErrorCode.INVALID_TRANSITION, "A job cannot move from " + from + " to " + target + ".",
                    Map.of("from", from.name(), "to", target.name(),
                            "allowed", from.allowedNext().stream().map(Enum::name).sorted().toList()));
        }
        job.moveTo(target, clock.instant());
        jobs.saveAndFlush(job);
        audit.record("JOB_STATUS_CHANGED", currentUser.id(), "JOB", id, Map.of("from", from.name(), "to", target.name()));
        return view(job);
    }

    private Job find(UUID id) {
        return jobs.findById(id).orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND, "Job not found."));
    }

    private void requireActiveClient(UUID clientId) {
        Client client = clients.findById(clientId).orElseThrow(() -> fieldError("clientId", "client does not exist"));
        if (!client.isActive()) {
            throw fieldError("clientId", "client is inactive");
        }
    }

    private static void validateSalary(BigDecimal min, BigDecimal max, String currency) {
        if ((min != null || max != null) && (currency == null || currency.isBlank())) {
            throw fieldError("currency", "is required when a salary is given");
        }
        if (min != null && max != null && max.compareTo(min) < 0) {
            throw fieldError("salaryMax", "must not be below the minimum salary");
        }
    }

    private Set<JobSkillEntry> validatedSkills(List<SkillInput> inputs) {
        List<SkillInput> skills = inputs == null ? List.of() : inputs;
        Set<UUID> ids = new HashSet<>();
        for (SkillInput skill : skills) {
            if (!ids.add(skill.skillId())) {
                throw fieldError("skills", "the same skill is listed more than once");
            }
        }
        if (!skillCatalog.unusable(ids).isEmpty()) {
            throw fieldError("skills", "contains a skill that does not exist or is inactive");
        }
        Set<JobSkillEntry> entries = new LinkedHashSet<>();
        skills.forEach(s -> entries.add(new JobSkillEntry(s.skillId(), s.importance(), s.minYears(), s.weight() == null ? 3 : s.weight())));
        return entries;
    }

    private static DomainException fieldError(String field, String message) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.",
                Map.of("errors", List.of(Map.of("field", field, "message", message))));
    }

    private Map<UUID, String> clientNames(Set<UUID> ids) {
        return clients.findAllById(ids).stream().collect(Collectors.toMap(Client::getId, Client::getName));
    }

    private JobView view(Job j) {
        Map<UUID, SkillRef> refs = skillCatalog.findByIds(j.getSkills().stream().map(JobSkillEntry::skillId).toList());
        List<JobSkillView> skills = new ArrayList<>();
        for (JobSkillEntry e : j.getSkills()) {
            SkillRef ref = refs.get(e.skillId());
            if (ref != null) {
                skills.add(new JobSkillView(ref.id(), ref.name(), ref.displayName(), e.importance(), e.minYears(), e.weight()));
            }
        }
        skills.sort(Comparator.comparing((JobSkillView s) -> s.importance().ordinal()).thenComparing(JobSkillView::displayName, String.CASE_INSENSITIVE_ORDER));
        String clientName = clients.findById(j.getClientId()).map(Client::getName).orElse(null);
        List<JobStatus> allowed = j.getStatus().allowedNext().stream().sorted().toList();
        return new JobView(j.getId(), j.getClientId(), clientName, j.getTitle(), j.getDescription(), j.getLocation(),
                j.getEmploymentType(), j.getSalaryMin(), j.getSalaryMax(), j.getCurrency(), j.getOpenings(), j.getStatus(),
                allowed, j.getOwnerId(), userLookup.displayNames(List.of(j.getOwnerId())).get(j.getOwnerId()),
                j.getOpenedAt(), j.getClosedAt(), skills, policy.canEdit(j), j.getCreatedAt(), j.getVersion());
    }
}
