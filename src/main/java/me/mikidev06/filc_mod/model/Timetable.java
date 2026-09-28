package me.mikidev06.filc_mod.model;

/** A published timetable revision. validTo is null while it is the current one. */
public record Timetable(String id, String name, String validFrom, String validTo) {
}
