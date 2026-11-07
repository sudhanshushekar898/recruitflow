package dev.recruitflow.interview.api;

import dev.recruitflow.identity.UserLookup;
import dev.recruitflow.interview.domain.InterviewStatus;
import dev.recruitflow.interview.domain.InterviewType;
import dev.recruitflow.interview.domain.ParticipantRole;
import dev.recruitflow.interview.domain.Recommendation;
import dev.recruitflow.interview.service.InterviewService;
import dev.recruitflow.interview.service.InterviewService.Changes;
import dev.recruitflow.interview.service.InterviewService.FeedbackInput;
import dev.recruitflow.interview.service.InterviewService.Filters;
import dev.recruitflow.interview.service.InterviewService.NewInterview;
import dev.recruitflow.interview.service.InterviewService.ParticipantInput;
import dev.recruitflow.interview.service.InterviewViews.FeedbackList;
import dev.recruitflow.interview.service.InterviewViews.FeedbackView;
import dev.recruitflow.interview.service.InterviewViews.InterviewView;
import dev.recruitflow.shared.paging.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/interviews")
class InterviewController {

    private final InterviewService interviews;

    InterviewController(InterviewService interviews) {
        this.interviews = interviews;
    }

    record ParticipantRequest(@NotNull UUID userId, ParticipantRole role) {
    }

    record ScheduleRequest(@NotNull UUID applicationId, @NotNull InterviewType type, @NotNull Instant start, @NotNull Instant end,
                           @Size(max = 300) String locationOrLink, @Size(max = 2000) String notes,
                           @NotEmpty @Valid List<ParticipantRequest> participants) {
    }

    /** Absent fields stay unchanged; a participants list replaces the panel. */
    record RescheduleRequest(Instant start, Instant end, InterviewType type, @Size(max = 300) String locationOrLink,
                             @Size(max = 2000) String notes, @Valid List<ParticipantRequest> participants, @NotNull Long version) {
    }

    record FeedbackRequest(@Min(1) @Max(5) int rating, @NotNull Recommendation recommendation,
                           @Size(max = 4000) String strengths, @Size(max = 4000) String concerns) {
    }

    @GetMapping
    PageResponse<InterviewView> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                     @RequestParam(required = false) InterviewStatus status,
                                     @RequestParam(required = false) UUID interviewerId,
                                     @RequestParam(required = false) UUID applicationId,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @RequestParam(required = false) String sort) {
        return interviews.list(new Filters(from, to, status, interviewerId, applicationId), page, size, sort);
    }

    @GetMapping("/mine")
    List<InterviewView> mine(@RequestParam(defaultValue = "all") String scope) {
        return interviews.mine(scope);
    }

    @GetMapping("/interviewers")
    List<UserLookup.Interviewer> interviewers() {
        return interviews.interviewers();
    }

    @PostMapping
    ResponseEntity<InterviewView> schedule(@Valid @RequestBody ScheduleRequest r) {
        InterviewView created = interviews.schedule(new NewInterview(r.applicationId(), r.type(), r.start(), r.end(),
                r.locationOrLink(), r.notes(), toInputs(r.participants())));
        return ResponseEntity.created(URI.create("/api/v1/interviews/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    InterviewView get(@PathVariable UUID id) {
        return interviews.get(id);
    }

    @PatchMapping("/{id}")
    InterviewView reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleRequest r) {
        return interviews.reschedule(id, new Changes(r.start(), r.end(), r.type(), r.locationOrLink(), r.notes(),
                r.participants() == null ? null : toInputs(r.participants())), r.version());
    }

    @PostMapping("/{id}/cancel")
    InterviewView cancel(@PathVariable UUID id) {
        return interviews.cancel(id);
    }

    @PostMapping("/{id}/complete")
    InterviewView complete(@PathVariable UUID id) {
        return interviews.complete(id);
    }

    @PostMapping("/{id}/feedback")
    ResponseEntity<FeedbackView> submitFeedback(@PathVariable UUID id, @Valid @RequestBody FeedbackRequest r) {
        return ResponseEntity.status(201).body(interviews.submitFeedback(id,
                new FeedbackInput(r.rating(), r.recommendation(), r.strengths(), r.concerns())));
    }

    @GetMapping("/{id}/feedback")
    FeedbackList feedback(@PathVariable UUID id) {
        return interviews.feedback(id);
    }

    private static List<ParticipantInput> toInputs(List<ParticipantRequest> participants) {
        return participants.stream().map(p -> new ParticipantInput(p.userId(), p.role())).toList();
    }
}
