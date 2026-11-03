package dev.recruitflow.job;

import dev.recruitflow.job.domain.Client;
import dev.recruitflow.job.domain.Job;
import dev.recruitflow.job.repository.ClientRepository;
import dev.recruitflow.job.repository.JobRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Public facade of the job module for other modules (the pipeline needs titles, clients and openings). */
@Component
public class JobLookup {

    private final JobRepository jobs;
    private final ClientRepository clients;

    JobLookup(JobRepository jobs, ClientRepository clients) {
        this.jobs = jobs;
        this.clients = clients;
    }

    @Transactional(readOnly = true)
    public Optional<JobRef> find(UUID id) {
        return Optional.ofNullable(findByIds(java.util.List.of(id)).get(id));
    }

    /** Two queries for any number of ids; unknown ids are absent from the result. */
    @Transactional(readOnly = true)
    public Map<UUID, JobRef> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        var found = jobs.findAllById(ids);
        Map<UUID, String> clientNames = clients.findAllById(found.stream().map(Job::getClientId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Client::getId, Client::getName));
        Map<UUID, JobRef> result = new HashMap<>();
        for (Job j : found) {
            result.put(j.getId(), new JobRef(j.getId(), j.getTitle(), clientNames.get(j.getClientId()),
                    j.getStatus().name(), j.getOpenings(), j.getOwnerId()));
        }
        return result;
    }
}
