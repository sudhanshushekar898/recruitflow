package dev.recruitflow.interview.repository;

import dev.recruitflow.interview.domain.Interview;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterviewRepository extends JpaRepository<Interview, UUID>, JpaSpecificationExecutor<Interview> {

    /** Scheduled interviews of one person that overlap the window (touching end to start is not an overlap). */
    @Query("""
            select distinct i from Interview i join i.participants p
            where p.userId = :userId and i.status = dev.recruitflow.interview.domain.InterviewStatus.SCHEDULED
              and i.scheduledStart < :end and i.scheduledEnd > :start and i.id <> :excludeId""")
    List<Interview> findOverlapping(@Param("userId") UUID userId, @Param("start") Instant start,
                                    @Param("end") Instant end, @Param("excludeId") UUID excludeId);

    @Query("select coalesce(max(i.roundNo), 0) from Interview i where i.applicationId = :applicationId")
    int maxRound(@Param("applicationId") UUID applicationId);

    @Query("select distinct i from Interview i join i.participants p where p.userId = :userId order by i.scheduledStart desc")
    List<Interview> findByParticipant(@Param("userId") UUID userId);
}
