package dev.recruitflow.skill;

import java.util.UUID;

/** What other modules may know about a skill. */
public record SkillRef(UUID id, String name, String displayName) {
}
