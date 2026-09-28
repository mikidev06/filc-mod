package me.mikidev06.filc_mod.model;

/** One slot in the bell schedule. Times are "HH:mm:ss" as the API sends them. */
public record Period(String id, int number, String startTime, String endTime) {
    /** "08:00" - the seconds the API sends are always zero and only add noise. */
    public String startShort() {
        return trim(startTime);
    }

    public String endShort() {
        return trim(endTime);
    }

    private static String trim(String time) {
        if (time == null) {
            return "";
        }
        return time.length() >= 5 ? time.substring(0, 5) : time;
    }
}
