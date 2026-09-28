package me.mikidev06.filc_mod.model;

import java.util.List;

/**
 * A single entry in a timetable. Several lessons can share a day and period when
 * a class splits into groups, so consumers must treat a day/period pair as a set
 * rather than a single lesson.
 */
public record Lesson(
        String id,
        NamedRef subject,
        List<NamedRef> teachers,
        List<NamedRef> classrooms,
        List<NamedRef> cohorts,
        List<Group> groups,
        Period period,
        DayDefinition day,
        WeekDefinition week) {

    public int dayNumber() {
        return day == null ? -1 : day.dayNumber();
    }

    public int periodNumber() {
        return period == null ? -1 : period.number();
    }

    public String subjectShort() {
        return subject == null ? "?" : subject.display();
    }

    /** Comma separated teacher names, or an empty string when none are set. */
    public String teacherNames() {
        return join(teachers, false);
    }

    /**
     * Comma separated room codes. Rooms are abbreviated deliberately: their full
     * names read "A.118 - Informatika", which is too long for a tooltip line and
     * repeats what the subject already says.
     */
    public String roomNames() {
        return join(classrooms, true);
    }

    /**
     * The group label to show in a cell that holds parallel lessons, e.g. "info1".
     * Empty when the lesson is for the whole class.
     */
    public String groupLabel() {
        if (groups == null || groups.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Group g : groups) {
            if (g.entireClass()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(g.name());
        }
        return sb.toString();
    }

    /** True when this lesson is taught to the whole class rather than a group. */
    public boolean isWholeClass() {
        return groupLabel().isEmpty();
    }

    /** "A" or "B" for alternating-week lessons, empty when it runs every week. */
    public String weekLabel() {
        if (week == null || week.everyWeek()) {
            return "";
        }
        return week.shortName() == null ? "" : week.shortName();
    }

    private static String join(List<NamedRef> refs, boolean preferShort) {
        if (refs == null || refs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (NamedRef ref : refs) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            String value = preferShort ? ref.display() : ref.name();
            sb.append(value == null ? ref.display() : value);
        }
        return sb.toString();
    }
}
