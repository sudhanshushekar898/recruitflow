package dev.recruitflow.candidate;

import dev.recruitflow.candidate.domain.CandidateStatus;
import dev.recruitflow.candidate.repository.CandidateRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Public facade of the candidate module for other modules. Anonymised candidates are never returned. */
@Component
public class CandidateLookup {

    private final CandidateRepository candidates;

    CandidateLookup(CandidateRepository candidates) {
        this.candidates = candidates;
    }

    @Transactional(readOnly = true)
    public Optional<CandidateRef> find(UUID id) {
        return Optional.ofNullable(findByIds(List.of(id)).get(id));
    }

    @Transactional(readOnly = true)
    public Map<UUID, CandidateRef> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return candidates.findAllById(ids).stream()
                .filter(c -> c.getStatus() != CandidateStatus.ANONYMISED)
                .collect(Collectors.toMap(c -> c.getId(),
                        c -> new CandidateRef(c.getId(), c.getFirstName() + " " + c.getLastName(), c.getEmail(),
                                c.getCurrentTitle(), c.getStatus().name())));
    }
}
