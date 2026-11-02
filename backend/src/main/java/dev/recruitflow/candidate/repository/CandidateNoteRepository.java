package dev.recruitflow.candidate.repository;

import dev.recruitflow.candidate.domain.CandidateNote;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateNoteRepository extends JpaRepository<CandidateNote, UUID> {

    List<CandidateNote> findByCandidateIdOrderByCreatedAtDesc(UUID candidateId, Pageable pageable);
}
