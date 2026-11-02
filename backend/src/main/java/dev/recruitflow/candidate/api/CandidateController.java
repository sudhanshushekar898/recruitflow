package dev.recruitflow.candidate.api;

import dev.recruitflow.candidate.api.CandidateDtos.ConsentRequest;
import dev.recruitflow.candidate.api.CandidateDtos.CreateCandidateRequest;
import dev.recruitflow.candidate.api.CandidateDtos.CreateNoteRequest;
import dev.recruitflow.candidate.api.CandidateDtos.ReplaceSkillsRequest;
import dev.recruitflow.candidate.api.CandidateDtos.SkillEntryRequest;
import dev.recruitflow.candidate.api.CandidateDtos.UpdateCandidateRequest;
import dev.recruitflow.candidate.domain.CandidateChanges;
import dev.recruitflow.candidate.domain.CandidateSource;
import dev.recruitflow.candidate.domain.CandidateStatus;
import dev.recruitflow.candidate.service.CandidateSearch;
import dev.recruitflow.candidate.service.CandidateService;
import dev.recruitflow.candidate.service.CandidateService.ConsentInput;
import dev.recruitflow.candidate.service.CandidateService.NewCandidate;
import dev.recruitflow.candidate.service.CandidateService.SkillInput;
import dev.recruitflow.candidate.service.CandidateViews.CandidateSummary;
import dev.recruitflow.candidate.service.CandidateViews.CandidateView;
import dev.recruitflow.candidate.service.CandidateViews.NoteView;
import dev.recruitflow.shared.paging.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/candidates")
class CandidateController {

    private final CandidateService candidates;

    CandidateController(CandidateService candidates) {
        this.candidates = candidates;
    }

    @GetMapping
    PageResponse<CandidateSummary> search(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) List<UUID> skillId,
                                          @RequestParam(required = false) String location,
                                          @RequestParam(required = false) CandidateStatus status,
                                          @RequestParam(required = false) UUID ownerId,
                                          @RequestParam(required = false) CandidateSource source,
                                          @RequestParam(required = false) Integer minYears,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(required = false) String sort) {
        return candidates.search(new CandidateSearch(q, skillId, location, status, ownerId, source, minYears), page, size, sort);
    }

    @PostMapping
    ResponseEntity<CandidateView> create(@Valid @RequestBody CreateCandidateRequest r) {
        CandidateView created = candidates.create(new NewCandidate(r.firstName(), r.lastName(), r.email(), r.phone(),
                r.currentTitle(), r.currentCompany(), r.location(), r.yearsExperience(), r.source(), r.summary(),
                r.linkedinUrl(), toSkillInputs(r.skills()), r.consent() == null ? null : toConsentInput(r.consent())));
        return ResponseEntity.created(URI.create("/api/v1/candidates/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    CandidateView get(@PathVariable UUID id) {
        return candidates.get(id);
    }

    @PatchMapping("/{id}")
    CandidateView update(@PathVariable UUID id, @Valid @RequestBody UpdateCandidateRequest r) {
        return candidates.update(id, new CandidateChanges(r.firstName(), r.lastName(), r.email(), r.phone(),
                r.currentTitle(), r.currentCompany(), r.location(), r.yearsExperience(), r.source(), r.summary(),
                r.linkedinUrl()), r.version());
    }

    @PostMapping("/{id}/archive")
    CandidateView archive(@PathVariable UUID id) {
        return candidates.archive(id);
    }

    @PostMapping("/{id}/restore")
    CandidateView restore(@PathVariable UUID id) {
        return candidates.restore(id);
    }

    @PutMapping("/{id}/skills")
    CandidateView replaceSkills(@PathVariable UUID id, @Valid @RequestBody ReplaceSkillsRequest r) {
        return candidates.replaceSkills(id, toSkillInputs(r.skills()));
    }

    @GetMapping("/{id}/notes")
    List<NoteView> notes(@PathVariable UUID id) {
        return candidates.listNotes(id);
    }

    @PostMapping("/{id}/notes")
    ResponseEntity<NoteView> addNote(@PathVariable UUID id, @Valid @RequestBody CreateNoteRequest r) {
        return ResponseEntity.status(201).body(candidates.addNote(id, r.body()));
    }

    @PutMapping("/{id}/consent")
    CandidateView recordConsent(@PathVariable UUID id, @Valid @RequestBody ConsentRequest r) {
        return candidates.recordConsent(id, toConsentInput(r));
    }

    private static List<SkillInput> toSkillInputs(List<SkillEntryRequest> skills) {
        return skills == null ? List.of() : skills.stream().map(s -> new SkillInput(s.skillId(), s.years(), s.level())).toList();
    }

    private static ConsentInput toConsentInput(ConsentRequest c) {
        return new ConsentInput(c.legalBasis(), c.givenAt(), c.expiresAt(), c.evidence(), c.withdrawn());
    }
}
