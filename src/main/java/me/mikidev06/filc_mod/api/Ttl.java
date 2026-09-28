package me.mikidev06.filc_mod.api;

import java.time.Duration;

/**
 * How long each kind of response stays usable.
 *
 * <p>Tuned against how often the data actually moves: reference lists are
 * rewritten a few times a year, a timetable revision lasts weeks, and
 * substitutions are published the evening before they apply.
 */
public final class Ttl {
    /** Cohorts, teachers, classrooms, periods - effectively static. */
    public static final Duration REFERENCE = Duration.ofHours(24);
    /** Which timetable revision is current. */
    public static final Duration TIMETABLE = Duration.ofHours(1);
    /** A class, teacher or room week. Large payloads, slow-moving content. */
    public static final Duration LESSONS = Duration.ofHours(6);
    /** Substitutions and moved lessons. */
    public static final Duration SUBSTITUTIONS = Duration.ofMinutes(5);

    private Ttl() {
    }
}
