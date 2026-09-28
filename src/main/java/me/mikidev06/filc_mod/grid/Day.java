package me.mikidev06.filc_mod.grid;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** The five school days, in the numbering the API uses ("1" is Monday). */
public enum Day {
    MONDAY(1, "Hetfo", "He"),
    TUESDAY(2, "Kedd", "Ke"),
    WEDNESDAY(3, "Szerda", "Sze"),
    THURSDAY(4, "Csutortok", "Cs"),
    FRIDAY(5, "Pentek", "Pe");

    private final int number;
    private final String displayName;
    private final String shortName;

    Day(int number, String displayName, String shortName) {
        this.number = number;
        this.displayName = displayName;
        this.shortName = shortName;
    }

    public int number() {
        return number;
    }

    /** Row index in the grid, 0 for Monday. */
    public int row() {
        return number - 1;
    }

    public String displayName() {
        return displayName;
    }

    public String shortName() {
        return shortName;
    }

    /** Translation key for the full day name, resolved by the client layer. */
    public String translationKey() {
        return "filc_mod.day." + number;
    }

    /** Translation key for the abbreviated day name shown beside the grid. */
    public String shortTranslationKey() {
        return "filc_mod.day.short." + number;
    }

    /** The day for an API day number, or null outside Monday to Friday. */
    public static Day ofNumber(int number) {
        for (Day day : values()) {
            if (day.number == number) {
                return day;
            }
        }
        return null;
    }

    /** Today, or null at the weekend. Drives the highlighted row. */
    public static Day today(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return ofNumber(dow.getValue());
    }
}
