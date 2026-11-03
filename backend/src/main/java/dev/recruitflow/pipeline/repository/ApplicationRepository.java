package dev.recruitflow.pipeline.repository;

import dev.recruitflow.pipeline.domain.Application;
import dev.recruitflow.pipeline.domain.ApplicationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ApplicationRepository extends JpaRepository<Application, UUID>, JpaSpecificationExecutor<Application> {

    Optional<Application> findByCandidateIdAndJobId(UUID candidateId, UUID jobId);

    List<Application> findByJobIdOrderByStatusChangedAtDesc(UUID jobId);

    List<Application> findByCandidateIdOrderByAppliedAtDesc(UUID candidateId);

    long countByJobIdAndStatus(UUID jobId, ApplicationStatus status);
}
