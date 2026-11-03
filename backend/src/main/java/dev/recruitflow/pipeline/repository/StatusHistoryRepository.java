package dev.recruitflow.pipeline.repository;

import dev.recruitflow.pipeline.domain.StatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, UUID> {

    List<StatusHistory> findByApplicationIdOrderByChangedAtAscIdAsc(UUID applicationId);
}
