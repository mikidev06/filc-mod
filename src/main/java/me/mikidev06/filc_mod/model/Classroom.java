package me.mikidev06.filc_mod.model;

/** A room. The API carries 3D layout fields too; none of them matter here. */
public record Classroom(String id, String name, String shortName) {
    public String display() {
        return shortName != null && !shortName.isBlank() ? shortName : name;
    }
}
