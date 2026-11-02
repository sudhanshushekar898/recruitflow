package dev.recruitflow.candidate.service;

import dev.recruitflow.candidate.domain.Candidate;
import dev.recruitflow.candidate.domain.CandidateSource;
import dev.recruitflow.candidate.domain.CandidateStatus;
import dev.recruitflow.shared.paging.Likes;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filters for the candidate list. Every filter is optional and they combine with AND.
 * q: each whitespace-separated word must appear somewhere in name, email, title, company or location.
 * skillIds: the candidate must have all of them.
 */
public record CandidateSearch(String q, List<UUID> skillIds, String location, CandidateStatus status,
                              UUID ownerId, CandidateSource source, Integer minYears) {

    Specification<Candidate> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Anonymised records are erased personal data: never listed.
            predicates.add(cb.notEqual(root.get("status"), CandidateStatus.ANONYMISED));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            } else {
                predicates.add(cb.notEqual(root.get("status"), CandidateStatus.ARCHIVED));
            }

            if (q != null && !q.isBlank()) {
                for (String token : q.trim().split("\\s+")) {
                    predicates.add(cb.like(root.get("searchText"), Likes.contains(token), Likes.ESCAPE));
                }
            }
            if (location != null && !location.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("location")), Likes.contains(location.trim().toLowerCase(Locale.ROOT)), Likes.ESCAPE));
            }
            if (ownerId != null) {
                predicates.add(cb.equal(root.get("ownerId"), ownerId));
            }
            if (source != null) {
                predicates.add(cb.equal(root.get("source"), source));
            }
            if (minYears != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("yearsExperience"), minYears));
            }
            if (skillIds != null) {
                for (UUID skillId : skillIds) {
                    Subquery<Integer> has = query.subquery(Integer.class);
                    var inner = has.correlate(root);
                    var entry = inner.join("skills");
                    has.select(cb.literal(1)).where(cb.equal(entry.get("skillId"), skillId));
                    predicates.add(cb.exists(has));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
