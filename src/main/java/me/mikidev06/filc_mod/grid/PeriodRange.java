package me.mikidev06.filc_mod.grid;

import java.util.Collection;
import me.mikidev06.filc_mod.model.Lesson;

/**
 * The span of periods a grid shows.
 *
 * <p>The bell schedule defines 17 periods, but only the first nine are the
 * ordinary school day and a chest is nine slots wide, so the range is derived
 * from the periods a timetable actually uses and then clamped to nine columns.
 */
public record PeriodRange(int first, int last) {

    public static final int MAX_COLUMNS = 9;
    private static final PeriodRange FALLBACK = new PeriodRange(0, 8);

    public int columns() {
        return last - first + 1;
    }

    public boolean contains(int period) {
        return period >= first && period <= last;
    }

    /** Column index for a period, or -1 when it falls outside the range. */
    public int column(int period) {
        return contains(period) ? period - first : -1;
    }

    /**
     * The range covering every lesson given, narrowed to the periods in use and
     * never wider than the grid. Empty input yields periods 0 to 8.
     */
    public static PeriodRange covering(Collection<Lesson> lessons) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (Lesson lesson : lessons) {
            int period = lesson.periodNumber();
            if (period < 0) {
                continue;
            }
            min = Math.min(min, period);
            max = Math.max(max, period);
        }
        if (min > max) {
            return FALLBACK;
        }
        if (max - min + 1 > MAX_COLUMNS) {
            // Keep the earliest periods; later ones are evening slots that a
            // normal class never uses.
            max = min + MAX_COLUMNS - 1;
        }
        return new PeriodRange(min, max);
    }
}
