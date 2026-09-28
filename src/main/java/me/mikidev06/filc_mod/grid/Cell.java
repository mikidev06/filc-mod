package me.mikidev06.filc_mod.grid;

import java.util.List;
import me.mikidev06.filc_mod.model.Lesson;

/**
 * One day-and-period position in the grid.
 *
 * <p>A cell holds a list rather than a single lesson because classes split into
 * groups: in the live data most occupied cells for a class carry two or three
 * parallel lessons taught to different groups in different rooms.
 */
public record Cell(Day day, int period, List<Lesson> lessons) {

    public boolean isEmpty() {
        return lessons.isEmpty();
    }

    public int size() {
        return lessons.size();
    }

    /** The lesson that best represents the cell when only one can be shown. */
    public Lesson primary() {
        if (lessons.isEmpty()) {
            return null;
        }
        for (Lesson lesson : lessons) {
            if (lesson.isWholeClass()) {
                return lesson;
            }
        }
        return lessons.get(0);
    }

    /**
     * True when the parallel lessons are all the same subject, which is the
     * common case for a split group and means the cell can be labelled once.
     */
    public boolean isSingleSubject() {
        if (lessons.size() <= 1) {
            return true;
        }
        String first = lessons.get(0).subjectShort();
        return lessons.stream().allMatch(l -> first.equals(l.subjectShort()));
    }
}
