package dev.recruitflow.pipeline;

import dev.recruitflow.pipeline.domain.Application;
import dev.recruitflow.pipeline.domain.ApplicationStatus;
import dev.recruitflow.pipeline.domain.StatusHistory;
import dev.recruitflow.pipeline.repository.ApplicationRepository;
import dev.recruitflow.pipeline.repository.StatusHistoryRepository;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Public facade of the pipeline module, used by the interview module. */
@Component
public class ApplicationLookup {

    private final ApplicationRepository applications;
    private final StatusHistoryRepository history;
    private final Clock clock;

    ApplicationLookup(ApplicationRepository applications, StatusHistoryRepository history, Clock clock) {
        this.applications = applications;
        this.history = history;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<ApplicationRef> find(UUID id) {
        return Optional.ofNullable(findByIds(List.of(id)).get(id));
    }

    @Transactional(readOnly = true)
    public Map<UUID, ApplicationRef> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return applications.findAllById(ids).stream().collect(Collectors.toMap(Application::getId,
                a -> new ApplicationRef(a.getId(), a.getCandidateId(), a.getJobId(), a.getStatus().name(), a.getOwnerId())));
    }

    /**
     * Scheduling an interview moves a shortlisted application to the interview stage, recorded in its history.
     * Joins the caller's transaction so the move and the interview commit together. Returns true if it moved.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean advanceToInterview(UUID applicationId, UUID actorId) {
        Application application = applications.findById(applicationId).orElse(null);
        if (application == null || application.getStatus() != ApplicationStatus.SHORTLISTED) {
            return false;
        }
        var now = clock.instant();
        application.moveTo(ApplicationStatus.INTERVIEW, null, now);
        applications.saveAndFlush(application);
        history.save(new StatusHistory(applicationId, ApplicationStatus.SHORTLISTED, ApplicationStatus.INTERVIEW, actorId, now,
                "Interview scheduled"));
        return true;
    }
}
