package me.mikidev06.filc_mod.model;

import java.util.List;

/**
 * Which weeks a lesson runs in. The API encodes this as a bitmask string:
 * "11" means every week, "10" is week A only and "01" is week B only.
 */
public record WeekDefinition(String id, String name, String shortName, List<String> weeks) {
    private String mask() {
        return weeks == null || weeks.isEmpty() ? "" : weeks.get(0);
    }

    /** True when the lesson runs every week and so needs no A/B annotation. */
    public boolean everyWeek() {
        String mask = mask();
        if (mask.isEmpty()) {
            return true;
        }
        return mask.chars().allMatch(c -> c == '1');
    }
}
