package dev.recruitflow.skill.repository;

import dev.recruitflow.skill.domain.Skill;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SkillRepository extends JpaRepository<Skill, UUID>, JpaSpecificationExecutor<Skill> {

    boolean existsByName(String name);

    @Query("select count(s) > 0 from Skill s join s.aliases a where a = :alias")
    boolean aliasExists(@Param("alias") String alias);
}
