package dev.recruitflow.skill.domain;

import dev.recruitflow.shared.persistence.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "skills")
public class Skill extends BaseEntity {

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 80)
    private String displayName;

    @Column(length = 40)
    private String category;

    private boolean active = true;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "skill_aliases", joinColumns = @JoinColumn(name = "skill_id"))
    @Column(name = "alias", nullable = false, length = 60)
    @BatchSize(size = 50)
    private Set<String> aliases = new HashSet<>();

    protected Skill() {}

    public Skill(String displayName, String category) {
        this.displayName = displayName.trim();
        this.name = normalize(displayName);
        this.category = category == null || category.isBlank() ? null : category.trim();
    }

    /** Canonical form used for uniqueness and lookups: trimmed, lower-case, single spaces. */
    public static String normalize(String raw) {
        return raw.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCategory() {
        return category;
    }

    public boolean isActive() {
        return active;
    }

    public Set<String> getAliases() {
        return Set.copyOf(aliases);
    }
}
