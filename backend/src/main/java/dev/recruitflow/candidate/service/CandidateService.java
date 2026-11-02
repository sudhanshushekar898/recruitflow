package dev.recruitflow.candidate.service;

import dev.recruitflow.audit.AuditRecorder;
import dev.recruitflow.candidate.domain.Candidate;
import dev.recruitflow.candidate.domain.CandidateChanges;
import dev.recruitflow.candidate.domain.CandidateConsent;
import dev.recruitflow.candidate.domain.CandidateNote;
import dev.recruitflow.candidate.domain.CandidateSkillEntry;
import dev.recruitflow.candidate.domain.CandidateSource;
import dev.recruitflow.candidate.domain.CandidateStatus;
import dev.recruitflow.candidate.domain.LegalBasis;
import dev.recruitflow.candidate.domain.SkillLevel;
import dev.recruitflow.candidate.repository.CandidateConsentRepository;
import dev.recruitflow.candidate.repository.CandidateNoteRepository;
import dev.recruitflow.candidate.repository.CandidateRepository;
import dev.recruitflow.candidate.service.CandidateViews.CandidateSummary;
import dev.recruitflow.candidate.service.CandidateViews.CandidateView;
import dev.recruitflow.candidate.service.CandidateViews.ConsentView;
import dev.recruitflow.candidate.service.CandidateViews.NoteView;
import dev.recruitflow.candidate.service.CandidateViews.SkillEntryView;
import dev.recruitflow.identity.CurrentUser;
import dev.recruitflow.identity.UserLookup;
import dev.recruitflow.shared.error.DomainException;
import dev.recruitflow.shared.error.ErrorCode;
import dev.recruitflow.shared.paging.PageRequests;
import dev.recruitflow.shared.paging.PageResponse;
import dev.recruitflow.skill.SkillCatalog;
import dev.recruitflow.skill.SkillRef;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Candidate management for recruiters and admins. Interviewer access arrives with the interview module. */
@Service
@PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
public class CandidateService {

    static final Set<String> SORT_FIELDS = Set.of("lastName", "firstName", "createdAt", "yearsExperience", "location");
    private static final int NOTES_LIMIT = 200;

    public record SkillInput(UUID skillId, Integer years, SkillLevel level) {
    }

    public record ConsentInput(LegalBasis legalBasis, Instant givenAt, Instant expiresAt, String evidence, boolean withdrawn) {
    }

    public record NewCandidate(String firstName, String lastName, String email, String phone, String currentTitle,
                               String currentCompany, String location, Integer yearsExperience, CandidateSource source,
                               String summary, String linkedinUrl, List<SkillInput> skills, ConsentInput consent) {
    }

    private final CandidateRepository candidates;
    private final CandidateNoteRepository notes;
    private final CandidateConsentRepository consents;
    private final SkillCatalog skillCatalog;
    private final UserLookup userLookup;
    private final CurrentUser currentUser;
    private final AuditRecorder audit;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final int retentionMonths;

    CandidateService(CandidateRepository candidates, CandidateNoteRepository notes, CandidateConsentRepository consents,
                     SkillCatalog skillCatalog, UserLookup userLookup, CurrentUser currentUser, AuditRecorder audit,
                     Clock clock, PlatformTransactionManager transactionManager,
                     @Value("${recruitflow.retention.default-months:24}") int retentionMonths) {
        this.tx = new TransactionTemplate(transactionManager);
        this.candidates = candidates;
        this.notes = notes;
        this.consents = consents;
        this.skillCatalog = skillCatalog;
        this.userLookup = userLookup;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
        this.retentionMonths = retentionMonths;
    }

    /**
     * Not transactional itself: when two requests race on the same email, PostgreSQL aborts the loser's
     * transaction, so the existing candidate can only be looked up after that transaction has rolled back.
     */
    public CandidateView create(NewCandidate in) {
        try {
            return tx.execute(status -> createInTransaction(in));
        } catch (DataIntegrityViolationException e) {
            String email = Candidate.normalizeEmail(in.email());
            throw candidates.findFirstByEmailAndStatusNot(email, CandidateStatus.ANONYMISED)
                    .map(existing -> duplicate(existing.getId()))
                    .orElseThrow(() -> e);
        }
    }

    private CandidateView createInTransaction(NewCandidate in) {
        String email = Candidate.normalizeEmail(in.email());
        candidates.findFirstByEmailAndStatusNot(email, CandidateStatus.ANONYMISED).ifPresent(existing -> {
            throw duplicate(existing.getId());
        });
        Set<CandidateSkillEntry> skills = validatedSkills(in.skills());
        Instant now = clock.instant();
        Candidate candidate = new Candidate(in.firstName(), in.lastName(), email, in.phone(), in.currentTitle(),
                in.currentCompany(), in.location(), in.yearsExperience(), in.source(), in.summary(), in.linkedinUrl(),
                currentUser.id(), defaultRetention(now));
        candidate.replaceSkills(skills);
        candidates.saveAndFlush(candidate);
        if (in.consent() != null) {
            applyConsent(candidate, in.consent(), now);
        }
        audit.record("CANDIDATE_CREATED", currentUser.id(), "CANDIDATE", candidate.getId(), Map.of("source", candidate.getSource().name()));
        return toView(candidate);
    }

    @Transactional(readOnly = true)
    public PageResponse<CandidateSummary> search(CandidateSearch search, int page, int size, String sort) {
        Pageable pageable = PageRequests.of(page, size, sort, SORT_FIELDS, "createdAt,desc");
        Page<Candidate> result = candidates.findAll(search.toSpecification(), pageable);

        Set<UUID> skillIds = new HashSet<>();
        result.forEach(c -> c.getSkills().forEach(s -> skillIds.add(s.skillId())));
        Map<UUID, SkillRef> skillRefs = skillCatalog.findByIds(skillIds);
        Map<UUID, String> owners = userLookup.displayNames(result.stream().map(Candidate::getOwnerId).collect(java.util.stream.Collectors.toSet()));

        return PageResponse.of(result, c -> new CandidateSummary(c.getId(), c.getFirstName() + " " + c.getLastName(),
                c.getEmail(), c.getCurrentTitle(), c.getLocation(), c.getYearsExperience(), c.getStatus(),
                c.getSkills().stream().map(s -> skillRefs.get(s.skillId())).filter(java.util.Objects::nonNull)
                        .map(SkillRef::displayName).sorted(String.CASE_INSENSITIVE_ORDER).toList(),
                owners.get(c.getOwnerId()), c.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public CandidateView get(UUID id) {
        return toView(find(id));
    }

    @Transactional
    public CandidateView update(UUID id, CandidateChanges changes, Long version) {
        Candidate candidate = find(id);
        candidate.checkVersion(version);
        requireEditable(candidate);
        if (changes.email() != null) {
            candidates.findFirstByEmailAndStatusNot(Candidate.normalizeEmail(changes.email()), CandidateStatus.ANONYMISED)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw duplicate(existing.getId());
                    });
        }
        List<String> changed = candidate.apply(changes);
        try {
            candidates.saveAndFlush(candidate);
        } catch (DataIntegrityViolationException e) {
            throw duplicate(null);
        }
        if (!changed.isEmpty()) {
            audit.record("CANDIDATE_UPDATED", currentUser.id(), "CANDIDATE", id, Map.of("fields", changed));
        }
        return toView(candidate);
    }

    @Transactional
    public CandidateView replaceSkills(UUID id, List<SkillInput> skills) {
        Candidate candidate = find(id);
        requireEditable(candidate);
        candidate.replaceSkills(validatedSkills(skills));
        candidates.saveAndFlush(candidate);
        audit.record("CANDIDATE_SKILLS_CHANGED", currentUser.id(), "CANDIDATE", id, Map.of("count", skills.size()));
        return toView(candidate);
    }

    @Transactional
    public CandidateView archive(UUID id) {
        Candidate candidate = find(id);
        if (candidate.isArchived()) {
            throw new DomainException(ErrorCode.INVALID_OPERATION, "The candidate is already archived.");
        }
        candidate.archive();
        candidates.saveAndFlush(candidate);
        audit.record("CANDIDATE_ARCHIVED", currentUser.id(), "CANDIDATE", id);
        return toView(candidate);
    }

    @Transactional
    public CandidateView restore(UUID id) {
        Candidate candidate = find(id);
        if (!candidate.isArchived()) {
            throw new DomainException(ErrorCode.INVALID_OPERATION, "Only archived candidates can be restored.");
        }
        candidate.restore();
        candidates.saveAndFlush(candidate);
        audit.record("CANDIDATE_RESTORED", currentUser.id(), "CANDIDATE", id);
        return toView(candidate);
    }

    @Transactional
    public NoteView addNote(UUID candidateId, String body) {
        find(candidateId);
        CandidateNote note = notes.saveAndFlush(new CandidateNote(candidateId, currentUser.id(), body));
        audit.record("CANDIDATE_NOTE_ADDED", currentUser.id(), "CANDIDATE", candidateId, Map.of("noteId", note.getId().toString()));
        return toNoteViews(List.of(note)).get(0);
    }

    @Transactional(readOnly = true)
    public List<NoteView> listNotes(UUID candidateId) {
        find(candidateId);
        return toNoteViews(notes.findByCandidateIdOrderByCreatedAtDesc(candidateId, PageRequest.of(0, NOTES_LIMIT)));
    }

    @Transactional
    public CandidateView recordConsent(UUID id, ConsentInput input) {
        Candidate candidate = find(id);
        applyConsent(candidate, input, clock.instant());
        audit.record("CANDIDATE_CONSENT_CHANGED", currentUser.id(), "CANDIDATE", id, Map.of("legalBasis", input.legalBasis().name()));
        return toView(candidate);
    }

    private void applyConsent(Candidate candidate, ConsentInput input, Instant now) {
        if (input.expiresAt() != null && !input.expiresAt().isAfter(input.givenAt())) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Consent expiry must be after it was given.",
                    Map.of("errors", List.of(Map.of("field", "expiresAt", "message", "must be after givenAt"))));
        }
        CandidateConsent consent = consents.findById(candidate.getId()).orElseGet(() -> new CandidateConsent(candidate.getId()));
        consent.record(input.legalBasis(), input.givenAt(), input.expiresAt(), input.evidence(), input.withdrawn() ? now : null);
        consents.saveAndFlush(consent);
        // The retention date follows the consent expiry when there is one, otherwise the default period.
        candidate.setRetentionUntil(input.expiresAt() != null ? input.expiresAt() : defaultRetention(now));
        candidates.saveAndFlush(candidate);
    }

    private Candidate find(UUID id) {
        return candidates.findById(id)
                .filter(c -> c.getStatus() != CandidateStatus.ANONYMISED)
                .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND, "Candidate not found."));
    }

    private static void requireEditable(Candidate candidate) {
        if (candidate.isArchived()) {
            throw new DomainException(ErrorCode.INVALID_OPERATION, "Restore the candidate before editing.");
        }
    }

    private Instant defaultRetention(Instant now) {
        return now.atZone(ZoneOffset.UTC).plusMonths(retentionMonths).toInstant();
    }

    /** Rejects duplicate or unknown/inactive skills and out-of-range values before anything is saved. */
    private Set<CandidateSkillEntry> validatedSkills(List<SkillInput> inputs) {
        List<SkillInput> skills = inputs == null ? List.of() : inputs;
        Set<UUID> ids = new HashSet<>();
        for (SkillInput skill : skills) {
            if (!ids.add(skill.skillId())) {
                throw invalidSkills("the same skill is listed more than once");
            }
        }
        if (!skillCatalog.unusable(ids).isEmpty()) {
            throw invalidSkills("contains a skill that does not exist or is inactive");
        }
        Set<CandidateSkillEntry> entries = new LinkedHashSet<>();
        skills.forEach(s -> entries.add(new CandidateSkillEntry(s.skillId(), s.years(), s.level())));
        return entries;
    }

    private static DomainException invalidSkills(String message) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.",
                Map.of("errors", List.of(Map.of("field", "skills", "message", message))));
    }

    private static DomainException duplicate(UUID existingId) {
        return new DomainException(ErrorCode.DUPLICATE_CANDIDATE, "A candidate with this email already exists.",
                existingId == null ? Map.of() : Map.of("existingCandidateId", existingId.toString()));
    }

    private CandidateView toView(Candidate c) {
        Map<UUID, SkillRef> refs = skillCatalog.findByIds(c.getSkills().stream().map(CandidateSkillEntry::skillId).toList());
        List<SkillEntryView> skills = new ArrayList<>();
        for (CandidateSkillEntry entry : c.getSkills()) {
            SkillRef ref = refs.get(entry.skillId());
            if (ref != null) {
                skills.add(new SkillEntryView(ref.id(), ref.name(), ref.displayName(), entry.years(), entry.level()));
            }
        }
        skills.sort(Comparator.comparing(SkillEntryView::displayName, String.CASE_INSENSITIVE_ORDER));
        ConsentView consent = consents.findById(c.getId())
                .map(k -> new ConsentView(k.getLegalBasis(), k.getGivenAt(), k.getExpiresAt(), k.getEvidence(), k.getWithdrawnAt()))
                .orElse(null);
        return new CandidateView(c.getId(), c.getFirstName(), c.getLastName(), c.getEmail(), c.getPhone(),
                c.getCurrentTitle(), c.getCurrentCompany(), c.getLocation(), c.getYearsExperience(), c.getSource(),
                c.getSummary(), c.getLinkedinUrl(), c.getStatus(), c.getOwnerId(),
                userLookup.displayNames(List.of(c.getOwnerId())).get(c.getOwnerId()), c.getRetentionUntil(), skills,
                consent, c.getCreatedAt(), c.getUpdatedAt(), c.getVersion());
    }

    private List<NoteView> toNoteViews(List<CandidateNote> list) {
        Map<UUID, String> authors = userLookup.displayNames(list.stream().map(CandidateNote::getAuthorId).collect(java.util.stream.Collectors.toSet()));
        return list.stream().map(n -> new NoteView(n.getId(), n.getAuthorId(), authors.get(n.getAuthorId()), n.getBody(), n.getCreatedAt())).toList();
    }
}
