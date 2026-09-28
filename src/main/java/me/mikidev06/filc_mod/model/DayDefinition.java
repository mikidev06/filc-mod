package me.mikidev06.filc_mod.model;

import java.util.List;

/**
 * The day a lesson falls on. The API models this as a list of day numbers so a
 * definition could in principle span several days; in the live data every
 * definition holds exactly one, "1" being Monday.
 */
public record DayDefinition(String id, String name, String shortName, List<String> days) {
    /** The first day number, or -1 when the definition is empty or malformed. */
    public int dayNumber() {
        if (days == null || days.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(days.get(0).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
