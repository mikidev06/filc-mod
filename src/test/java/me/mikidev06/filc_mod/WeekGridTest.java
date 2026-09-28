package me.mikidev06.filc_mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import me.mikidev06.filc_mod.api.FilcJson;
import me.mikidev06.filc_mod.grid.Cell;
import me.mikidev06.filc_mod.grid.Day;
import me.mikidev06.filc_mod.grid.PeriodRange;
import me.mikidev06.filc_mod.grid.WeekGrid;
import me.mikidev06.filc_mod.model.Lesson;
import org.junit.jupiter.api.Test;

class WeekGridTest {

    private static List<Lesson> ninthA() {
        return FilcJson.mapArray(Fixtures.data("lessons-9a.json"), FilcJson::lesson);
    }

    @Test
    void bucketsEveryLessonIntoTheWeek() {
        WeekGrid grid = WeekGrid.build(ninthA(), null, Set.of());
        assertEquals(68, grid.lessonCount());
        assertFalse(grid.isEmpty());
    }

    @Test
    void keepsParallelGroupLessonsInOneCell() {
        WeekGrid grid = WeekGrid.build(ninthA(), null, Set.of());
        Cell cell = grid.cell(Day.MONDAY, 4);
        // Monday period 4 is a split: info1 is co-taught and info2 runs elsewhere.
        assertEquals(3, cell.size());
        assertTrue(cell.isSingleSubject());
        assertEquals("DiKu", cell.primary().subjectShort());
    }

    @Test
    void filteringByGroupCollapsesTheParallelLessons() {
        WeekGrid grid = WeekGrid.build(ninthA(), "info2", Set.of());
        Cell cell = grid.cell(Day.MONDAY, 4);
        assertEquals(1, cell.size());
        assertEquals("info2", cell.lessons().get(0).groupLabel());
    }

    @Test
    void groupFilteringKeepsWholeClassLessons() {
        List<Lesson> lessons = ninthA();
        long wholeClass = lessons.stream().filter(Lesson::isWholeClass).count();
        WeekGrid filtered = WeekGrid.build(lessons, "info2", Set.of());
        long keptWholeClass = 0;
        for (Day day : Day.values()) {
            for (int period = 0; period <= 8; period++) {
                keptWholeClass += filtered.cell(day, period).lessons().stream()
                        .filter(Lesson::isWholeClass)
                        .count();
            }
        }
        assertEquals(wholeClass, keptWholeClass,
                "a group filter must not hide lessons taught to everyone");
    }

    @Test
    void groupFilterIsCaseInsensitive() {
        WeekGrid grid = WeekGrid.build(ninthA(), "INFO2", Set.of());
        assertEquals(1, grid.cell(Day.MONDAY, 4).size());
    }

    @Test
    void emptyCellsAreReturnedRatherThanNull() {
        WeekGrid grid = WeekGrid.build(ninthA(), null, Set.of());
        Cell cell = grid.cell(Day.FRIDAY, 8);
        assertTrue(cell.isEmpty());
        assertNull(cell.primary());
    }

    @Test
    void mapsDaysAndPeriodsOntoChestSlots() {
        WeekGrid grid = WeekGrid.build(ninthA(), null, Set.of());
        PeriodRange range = grid.range();
        assertEquals(1, range.first(), "9.A has no zero period");
        assertEquals(8, range.last());

        // Monday, the first period in range, is the very first slot.
        assertEquals(0, grid.slotIndex(Day.MONDAY, range.first()));
        // Friday sits on the fifth row.
        assertEquals(4 * 9, grid.slotIndex(Day.FRIDAY, range.first()));
        // The last period in range sits in the last used column: 9.A runs
        // periods 1 to 8, so that is column 7, not 8.
        assertEquals(7, grid.slotIndex(Day.MONDAY, range.last()));
        // Anything outside the range has no slot.
        assertEquals(-1, grid.slotIndex(Day.MONDAY, 0));
        assertEquals(-1, grid.slotIndex(Day.MONDAY, 12));
    }

    @Test
    void flagsLessonsThatHaveASubstitutionAgainstThem() {
        List<Lesson> lessons = ninthA();
        Lesson target = lessons.stream()
                .filter(l -> l.dayNumber() == 1 && l.periodNumber() == 4)
                .findFirst()
                .orElseThrow();
        WeekGrid grid = WeekGrid.build(lessons, null, Set.of(target.id()));

        assertTrue(grid.isSubstituted(target));
        assertTrue(grid.hasSubstitution(grid.cell(Day.MONDAY, 4)));
        assertFalse(grid.hasSubstitution(grid.cell(Day.TUESDAY, 4)));
    }

    @Test
    void skipsLessonsOutsideMondayToFriday() {
        // The grid has five rows, so a weekend or malformed day must be dropped
        // rather than land in a slot that does not exist.
        List<Lesson> lessons = ninthA();
        WeekGrid grid = WeekGrid.build(lessons, null, Set.of());
        int reachable = 0;
        for (Day day : Day.values()) {
            for (int period = grid.range().first(); period <= grid.range().last(); period++) {
                reachable += grid.cell(day, period).size();
            }
        }
        assertEquals(grid.lessonCount(), reachable,
                "every bucketed lesson must be reachable through a day and period");
    }

    @Test
    void handlesAnEmptyTimetable() {
        WeekGrid grid = WeekGrid.build(List.of(), null, Set.of());
        assertTrue(grid.isEmpty());
        assertEquals(0, grid.lessonCount());
        assertEquals(new PeriodRange(0, 8), grid.range());
    }
}
