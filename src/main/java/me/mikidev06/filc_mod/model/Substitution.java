package me.mikidev06.filc_mod.model;

import java.time.LocalDate;

/** The substitution record itself, without the teacher or affected lessons. */
public record Substitution(String id, LocalDate date, String comment, String substituterId) {
}
