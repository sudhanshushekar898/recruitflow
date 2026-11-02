package dev.recruitflow.candidate.repository;

import dev.recruitflow.candidate.domain.Candidate;
import dev.recruitflow.candidate.domain.CandidateStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CandidateRepository extends JpaRepository<Candidate, UUID>, JpaSpecificationExecutor<Candidate> {

    /** Emails are stored lower-case, so an exact match is a case-insensitive match. */
    Optional<Candidate> findFirstByEmailAndStatusNot(String email, CandidateStatus excluded);
}
