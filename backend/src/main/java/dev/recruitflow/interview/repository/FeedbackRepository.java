package dev.recruitflow.interview.repository;

import dev.recruitflow.interview.domain.InterviewFeedback;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<InterviewFeedback, UUID> {

    List<InterviewFeedback> findByInterviewIdOrderBySubmittedAtAsc(UUID interviewId);

    List<InterviewFeedback> findByInterviewIdIn(Collection<UUID> interviewIds);

    boolean existsByInterviewIdAndInterviewerId(UUID interviewId, UUID interviewerId);
}
