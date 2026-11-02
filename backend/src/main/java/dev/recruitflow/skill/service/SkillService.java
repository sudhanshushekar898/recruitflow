package dev.recruitflow.skill.service;

import dev.recruitflow.shared.error.DomainException;
import dev.recruitflow.shared.error.ErrorCode;
import dev.recruitflow.shared.paging.Likes;
import dev.recruitflow.skill.domain.Skill;
import dev.recruitflow.skill.repository.SkillRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillService {

    private static final int AUTOCOMPLETE_LIMIT = 20;

    private final SkillRepository skills;

    SkillService(SkillRepository skills) {
        this.skills = skills;
    }

    public record SkillView(UUID id, String name, String displayName, String category) {
        static SkillView from(Skill skill) {
            return new SkillView(skill.getId(), skill.getName(), skill.getDisplayName(), skill.getCategory());
        }
    }

    /** Autocomplete: matches the name, display name or any alias; an empty query lists the first skills alphabetically. */
    @Transactional(readOnly = true)
    public List<SkillView> search(String q) {
        Specification<Skill> spec = (root, query, cb) -> {
            query.distinct(true);
            Predicate active = cb.isTrue(root.get("active"));
            if (q == null || q.isBlank()) {
                return active;
            }
            String pattern = Likes.contains(q.trim());
            var alias = root.<Skill, String>join("aliases", JoinType.LEFT);
            return cb.and(active, cb.or(
                    cb.like(root.get("name"), pattern, Likes.ESCAPE),
                    cb.like(cb.lower(root.get("displayName")), pattern, Likes.ESCAPE),
                    cb.like(alias, pattern, Likes.ESCAPE)));
        };
        return skills.findAll(spec, PageRequest.of(0, AUTOCOMPLETE_LIMIT, Sort.by("name"))).stream().map(SkillView::from).toList();
    }

    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @Transactional
    public SkillView create(String displayName, String category) {
        String name = Skill.normalize(displayName);
        if (skills.existsByName(name) || skills.aliasExists(name)) {
            throw duplicate();
        }
        try {
            return SkillView.from(skills.saveAndFlush(new Skill(displayName, category)));
        } catch (DataIntegrityViolationException e) { // concurrent create of the same skill
            throw duplicate();
        }
    }

    private static DomainException duplicate() {
        return new DomainException(ErrorCode.DUPLICATE_SKILL, "A skill with this name or alias already exists.");
    }
}
