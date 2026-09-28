package me.mikidev06.filc_mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import me.mikidev06.filc_mod.api.FilcJson;
import me.mikidev06.filc_mod.grid.PeriodRange;
import me.mikidev06.filc_mod.model.DayDefinition;
import me.mikidev06.filc_mod.model.Lesson;
import me.mikidev06.filc_mod.model.Period;
import org.junit.jupiter.api.Test;

class PeriodRangeTest {

    private static Lesson at(int period) {
        return new Lesson(
                "id-" + period,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new Period("p" + period, period, "08:00:00", "08:45:00"),
                new DayDefinition("d", "Hetfo", "He", List.of("1")),
                null);
    }

    @Test
    void coversOnlyThePeriodsInUse() {
        PeriodRange range = PeriodRange.covering(List.of(at(1), at(5), at(3)));
        assertEquals(1, range.first());
        assertEquals(5, range.last());
        assertEquals(5, range.columns());
    }

    @Test
    void clampsToTheNineColumnsAChestHas() {
        // The bell schedule defines 17 periods; the grid can only show nine.
        PeriodRange range = PeriodRange.covering(List.of(at(0), at(16)));
        assertEquals(0, range.first());
        assertEquals(8, range.last());
        assertEquals(PeriodRange.MAX_COLUMNS, range.columns());
        assertFalse(range.contains(9), "evening periods fall off the grid");
    }

    @Test
    void clampingKeepsTheEarliestPeriods() {
        PeriodRange range = PeriodRange.covering(List.of(at(2), at(15)));
        assertEquals(2, range.first());
        assertEquals(10, range.last());
    }

    @Test
    void fallsBackToTheOrdinarySchoolDayWhenThereAreNoLessons() {
        PeriodRange range = PeriodRange.covering(List.of());
        assertEquals(0, range.first());
        assertEquals(8, range.last());
    }

    @Test
    void ignoresLessonsWithNoPeriod() {
        Lesson broken = new Lesson("broken", null, List.of(), List.of(), List.of(), List.of(),
                null, null, null);
        PeriodRange range = PeriodRange.covering(List.of(broken, at(4)));
        assertEquals(4, range.first());
        assertEquals(4, range.last());
    }

    @Test
    void mapsPeriodsToColumns() {
        PeriodRange range = new PeriodRange(1, 8);
        assertEquals(0, range.column(1));
        assertEquals(7, range.column(8));
        assertEquals(-1, range.column(0));
        assertEquals(-1, range.column(9));
        assertTrue(range.contains(4));
    }

    @Test
    void derivesTheRealRangeFromTheLiveTimetable() {
        List<Lesson> lessons = FilcJson.mapArray(Fixtures.data("lessons-9a.json"), FilcJson::lesson);
        PeriodRange range = PeriodRange.covering(lessons);
        assertEquals(1, range.first());
        assertEquals(8, range.last());
        assertEquals(8, range.columns());
    }
}
