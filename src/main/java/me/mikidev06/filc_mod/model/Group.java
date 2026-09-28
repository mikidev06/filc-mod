package me.mikidev06.filc_mod.model;

/**
 * A division of a class. A cohort always has an "Egesz osztaly" group with
 * entireClass set, plus paired groups such as gy1/gy2 or info1/info2 that share
 * a divisionLabel and run in parallel during the same period.
 */
public record Group(
        String id,
        String name,
        String divisionTag,
        String divisionLabel,
        boolean entireClass) {
}
