package me.mikidev06.filc_mod.model;

/**
 * A teacher as returned by /timetable/teachers/getAll and the substitution
 * endpoints. Names are stored in Hungarian order, so firstName holds the family
 * name and displayName() joins them without reordering.
 */
public record Teacher(String id, String firstName, String lastName, String shortName) {
    public String displayName() {
        if (firstName == null || firstName.isBlank()) {
            return lastName == null ? shortName : lastName;
        }
        if (lastName == null || lastName.isBlank()) {
            return firstName;
        }
        return firstName + " " + lastName;
    }
}
