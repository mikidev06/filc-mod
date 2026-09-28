package me.mikidev06.filc_mod.client.gui;

import java.util.List;
import java.util.Map;
import me.mikidev06.filc_mod.grid.WeekGrid;
import me.mikidev06.filc_mod.model.Lesson;
import me.mikidev06.filc_mod.model.Period;
import me.mikidev06.filc_mod.model.SubstitutionEntry;

/**
 * Everything the timetable screen needs, assembled off the render thread.
 *
 * @param heading       what the week belongs to, e.g. "9.A" or "Meszaros Tamas"
 * @param subheading    the timetable revision name
 * @param grid          the lessons, already filtered and bucketed
 * @param periods       bell times by period number, for the tooltips
 * @param cohortId      the class this week is for, or null for teacher and room
 *                      views, which have no substitution endpoint of their own
 * @param groupFilter   the group the grid was narrowed to, or null
 * @param substitutions substitutions covering this class
 * @param lessonsById   every lesson in the week, so a substitution can name the
 *                      lesson it replaces from the ids the API returns
 */
public record TimetableView(
        String heading,
        String subheading,
        WeekGrid grid,
        Map<Integer, Period> periods,
        String cohortId,
        String groupFilter,
        List<SubstitutionEntry> substitutions,
        Map<String, Lesson> lessonsById) {
}
