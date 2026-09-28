package me.mikidev06.filc_mod.model;

import java.util.List;

/**
 * A substitution together with the covering teacher and the ids of the lessons
 * it affects. The cohort-scoped endpoint returns lesson ids rather than whole
 * lessons, so callers resolve them against a timetable they already hold.
 */
public record SubstitutionEntry(Substitution substitution, Teacher teacher, List<String> lessonIds) {
}
