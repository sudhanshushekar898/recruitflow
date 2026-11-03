package dev.recruitflow.job.api;

import dev.recruitflow.job.api.JobDtos.ChangeJobStatusRequest;
import dev.recruitflow.job.api.JobDtos.CreateJobRequest;
import dev.recruitflow.job.api.JobDtos.ReplaceJobSkillsRequest;
import dev.recruitflow.job.api.JobDtos.SkillRequirementRequest;
import dev.recruitflow.job.api.JobDtos.UpdateJobRequest;
import dev.recruitflow.job.domain.JobStatus;
import dev.recruitflow.job.service.JobService;
import dev.recruitflow.job.service.JobService.JobFilters;
import dev.recruitflow.job.service.JobService.NewJob;
import dev.recruitflow.job.service.JobService.SkillInput;
import dev.recruitflow.job.service.JobViews.JobSummary;
import dev.recruitflow.job.service.JobViews.JobView;
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
@RequestMapping("/api/v1/jobs")
class JobController {

    private final JobService jobs;

    JobController(JobService jobs) {
        this.jobs = jobs;
    }

    @GetMapping
    PageResponse<JobSummary> search(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) JobStatus status,
                                    @RequestParam(required = false) UUID clientId,
                                    @RequestParam(required = false) UUID ownerId,
                                    @RequestParam(required = false) String location,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String sort) {
        return jobs.search(new JobFilters(q, status, clientId, ownerId, location), page, size, sort);
    }

    @PostMapping
    ResponseEntity<JobView> create(@Valid @RequestBody CreateJobRequest r) {
        JobView created = jobs.create(new NewJob(r.clientId(), r.title(), r.description(), r.location(), r.employmentType(),
                r.salaryMin(), r.salaryMax(), r.currency(), r.openings(), toSkills(r.skills())));
        return ResponseEntity.created(URI.create("/api/v1/jobs/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    JobView get(@PathVariable UUID id) {
        return jobs.get(id);
    }

    @PatchMapping("/{id}")
    JobView update(@PathVariable UUID id, @Valid @RequestBody UpdateJobRequest r) {
        return jobs.update(id, new NewJob(r.clientId(), r.title(), r.description(), r.location(), r.employmentType(),
                r.salaryMin(), r.salaryMax(), r.currency(), r.openings(), null), r.version());
    }

    @PutMapping("/{id}/skills")
    JobView replaceSkills(@PathVariable UUID id, @Valid @RequestBody ReplaceJobSkillsRequest r) {
        return jobs.replaceSkills(id, toSkills(r.skills()));
    }

    @PostMapping("/{id}/status")
    JobView changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeJobStatusRequest r) {
        return jobs.changeStatus(id, r.toStatus());
    }

    private static List<SkillInput> toSkills(List<SkillRequirementRequest> skills) {
        return skills == null ? List.of()
                : skills.stream().map(s -> new SkillInput(s.skillId(), s.importance(), s.minYears(), s.weight())).toList();
    }
}
