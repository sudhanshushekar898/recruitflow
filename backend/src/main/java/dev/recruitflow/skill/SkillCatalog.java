package dev.recruitflow.skill;

import dev.recruitflow.skill.domain.Skill;
import dev.recruitflow.skill.repository.SkillRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Public facade of the skill module: lookups other modules need, without exposing entities. */
@Component
public class SkillCatalog {

    private final SkillRepository skills;

    SkillCatalog(SkillRepository skills) {
        this.skills = skills;
    }

    /** One query for all ids; unknown ids are absent from the result. */
    @Transactional(readOnly = true)
    public Map<UUID, SkillRef> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, SkillRef> result = new HashMap<>();
        for (Skill skill : skills.findAllById(ids)) {
            result.put(skill.getId(), new SkillRef(skill.getId(), skill.getName(), skill.getDisplayName()));
        }
        return result;
    }

    /** Ids that do not exist or are inactive; empty means every id may be used. */
    @Transactional(readOnly = true)
    public Set<UUID> unusable(Collection<UUID> ids) {
        Set<UUID> unusable = new HashSet<>(ids);
        for (Skill skill : skills.findAllById(ids)) {
            if (skill.isActive()) {
                unusable.remove(skill.getId());
            }
        }
        return unusable;
    }
}
