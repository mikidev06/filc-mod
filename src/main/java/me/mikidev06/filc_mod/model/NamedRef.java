package me.mikidev06.filc_mod.model;

/**
 * The {id, name, short} triple the API embeds for subjects, classrooms, cohorts
 * and the teachers attached to a lesson.
 */
public record NamedRef(String id, String name, String shortName) {
    public String display() {
        return shortName != null && !shortName.isBlank() ? shortName : name;
    }
}
