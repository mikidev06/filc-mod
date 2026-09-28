package me.mikidev06.filc_mod.grid;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import me.mikidev06.filc_mod.model.Group;
import me.mikidev06.filc_mod.model.Lesson;

/**
 * A week of lessons arranged as rows of days and columns of periods, ready to
 * be painted into the 9x6 slot grid of a chest.
 */
public final class WeekGrid {

    private final Map<Day, Map<Integer, Cell>> cells = new EnumMap<>(Day.class);
    private final PeriodRange range;
    private final Set<String> substitutedLessonIds;

    private WeekGrid(PeriodRange range, Set<String> substitutedLessonIds) {
        this.range = range;
        this.substitutedLessonIds = substitutedLessonIds;
    }

    /**
     * Builds a grid from a lesson list.
     *
     * @param groupFilter when non-null, keeps only whole-class lessons and
     *                    lessons belonging to the named group, collapsing the
     *                    parallel entries a split class produces
     */
    public static WeekGrid build(List<Lesson> lessons, String groupFilter, Set<String> substitutedLessonIds) {
        List<Lesson> kept = new ArrayList<>();
        for (Lesson lesson : lessons) {
            if (lesson.dayNumber() < 1 || lesson.periodNumber() < 0) {
                continue;
            }
            if (Day.ofNumber(lesson.dayNumber()) == null) {
                continue;
            }
            if (matchesGroup(lesson, groupFilter)) {
                kept.add(lesson);
            }
        }

        WeekGrid grid = new WeekGrid(
                PeriodRange.covering(kept),
                substitutedLessonIds == null ? Set.of() : new HashSet<>(substitutedLessonIds));

        for (Lesson lesson : kept) {
            Day day = Day.ofNumber(lesson.dayNumber());
            grid.cells
                    .computeIfAbsent(day, d -> new java.util.HashMap<>())
                    .computeIfAbsent(lesson.periodNumber(), p -> new Cell(day, p, new ArrayList<>()))
                    .lessons()
                    .add(lesson);
        }
        return grid;
    }

    static boolean matchesGroup(Lesson lesson, String groupFilter) {
        if (groupFilter == null || groupFilter.isBlank()) {
            return true;
        }
        if (lesson.isWholeClass()) {
            return true;
        }
        List<Group> groups = lesson.groups();
        if (groups == null) {
            return false;
        }
        for (Group group : groups) {
            if (group.name() != null
                    && group.name().toLowerCase(Locale.ROOT).equals(groupFilter.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public PeriodRange range() {
        return range;
    }

    /** The cell at a position, or an empty cell when nothing is scheduled. */
    public Cell cell(Day day, int period) {
        Map<Integer, Cell> row = cells.get(day);
        if (row == null) {
            return new Cell(day, period, List.of());
        }
        Cell cell = row.get(period);
        return cell == null ? new Cell(day, period, List.of()) : cell;
    }

    public boolean isSubstituted(Lesson lesson) {
        return lesson != null && lesson.id() != null && substitutedLessonIds.contains(lesson.id());
    }

    /** True when any lesson in the cell has a substitution against it. */
    public boolean hasSubstitution(Cell cell) {
        return cell.lessons().stream().anyMatch(this::isSubstituted);
    }

    public int lessonCount() {
        int total = 0;
        for (Map<Integer, Cell> row : cells.values()) {
            for (Cell cell : row.values()) {
                total += cell.size();
            }
        }
        return total;
    }

    public boolean isEmpty() {
        return lessonCount() == 0;
    }

    /**
     * Slot index in a 9-wide chest for a day and period, or -1 when the position
     * falls outside the grid.
     */
    public int slotIndex(Day day, int period) {
        int column = range.column(period);
        if (column < 0 || column >= PeriodRange.MAX_COLUMNS) {
            return -1;
        }
        return day.row() * PeriodRange.MAX_COLUMNS + column;
    }
}
