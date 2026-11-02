package dev.recruitflow.candidate.repository;

import dev.recruitflow.candidate.domain.CandidateConsent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateConsentRepository extends JpaRepository<CandidateConsent, UUID> {
}
