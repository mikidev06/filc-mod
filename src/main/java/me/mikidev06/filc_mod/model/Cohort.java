package me.mikidev06.filc_mod.model;

/** A class such as 9.A or 12.KNY. */
public record Cohort(String id, String name, String shortName, String teacherId) {
    public String display() {
        return shortName != null && !shortName.isBlank() ? shortName : name;
    }
}
