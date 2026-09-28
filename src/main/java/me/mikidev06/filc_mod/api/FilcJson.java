package me.mikidev06.filc_mod.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import me.mikidev06.filc_mod.model.Classroom;
import me.mikidev06.filc_mod.model.Cohort;
import me.mikidev06.filc_mod.model.DayDefinition;
import me.mikidev06.filc_mod.model.Group;
import me.mikidev06.filc_mod.model.Lesson;
import me.mikidev06.filc_mod.model.NamedRef;
import me.mikidev06.filc_mod.model.Period;
import me.mikidev06.filc_mod.model.Substitution;
import me.mikidev06.filc_mod.model.SubstitutionEntry;
import me.mikidev06.filc_mod.model.Teacher;
import me.mikidev06.filc_mod.model.Timetable;
import me.mikidev06.filc_mod.model.WeekDefinition;

/**
 * Turns Chronos responses into model records.
 *
 * <p>Parsing is written against JsonObject rather than bound reflectively,
 * because the wire format uses "short" as a field name and that cannot be a
 * record component. Doing it by hand also keeps missing and null fields from
 * becoming surprises at render time.
 */
public final class FilcJson {

    private FilcJson() {
    }

    /**
     * Unwraps the {data, success} envelope every Chronos endpoint returns.
     *
     * @throws FilcApiException when the envelope reports failure
     */
    public static JsonElement unwrap(String body, int statusCode, long retryAfterSeconds) {
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonObject()) {
                throw new FilcApiException("Response was not a JSON object", "MALFORMED", statusCode, retryAfterSeconds);
            }
            root = parsed.getAsJsonObject();
        } catch (JsonSyntaxException e) {
            throw new FilcApiException("Could not parse response as JSON", e);
        }

        boolean success = root.has("success") && !root.get("success").isJsonNull()
                && root.get("success").getAsBoolean();
        if (!success || statusCode >= 400) {
            String message = str(root, "error");
            String code = str(root, "code");
            throw new FilcApiException(
                    message == null ? "Request failed with HTTP " + statusCode : message,
                    code == null ? "UNKNOWN" : code,
                    statusCode,
                    retryAfterSeconds);
        }
        JsonElement data = root.get("data");
        return data == null ? com.google.gson.JsonNull.INSTANCE : data;
    }

    // ---- element helpers -------------------------------------------------

    static String str(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        return obj.get(key).getAsString();
    }

    static int intVal(JsonObject obj, String key, int fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static boolean boolVal(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return false;
        }
        return obj.get(key).getAsBoolean();
    }

    static JsonObject obj(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonObject()) {
            return null;
        }
        return parent.getAsJsonObject(key);
    }

    static List<String> strings(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonElement el : parent.getAsJsonArray(key)) {
            if (!el.isJsonNull()) {
                out.add(el.getAsString());
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static <T> List<T> mapArray(JsonElement element, Function<JsonObject, T> mapper) {
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        JsonArray array = element.getAsJsonArray();
        List<T> out = new ArrayList<>(array.size());
        for (JsonElement el : array) {
            if (el.isJsonObject()) {
                T mapped = mapper.apply(el.getAsJsonObject());
                if (mapped != null) {
                    out.add(mapped);
                }
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static <T> List<T> mapChildArray(JsonObject parent, String key, Function<JsonObject, T> mapper) {
        if (parent == null || !parent.has(key)) {
            return List.of();
        }
        return mapArray(parent.get(key), mapper);
    }

    // ---- record parsers --------------------------------------------------

    public static NamedRef namedRef(JsonObject o) {
        if (o == null) {
            return null;
        }
        return new NamedRef(str(o, "id"), str(o, "name"), str(o, "short"));
    }

    public static Timetable timetable(JsonObject o) {
        if (o == null) {
            return null;
        }
        return new Timetable(str(o, "id"), str(o, "name"), str(o, "validFrom"), str(o, "validTo"));
    }

    public static Cohort cohort(JsonObject o) {
        if (o == null) {
            return null;
        }
        return new Cohort(str(o, "id"), str(o, "name"), str(o, "short"), str(o, "teacherId"));
    }

    public static Classroom classroom(JsonObject o) {
        if (o == null) {
            return null;
        }
        return new Classroom(str(o, "id"), str(o, "name"), str(o, "short"));
    }

    public static Teacher teacher(JsonObject o) {
        if (o == null) {
            return null;
        }
        return new Teacher(str(o, "id"), str(o, "firstName"), str(o, "lastName"), str(o, "short"));
    }

    public static Group group(JsonObject o) {
        if (o == null) {
            return null;
        }
        return new Group(
                str(o, "id"),
                str(o, "name"),
                str(o, "divisionTag"),
                str(o, "divisionLabel"),
                boolVal(o, "entireClass"));
    }

    public static Period period(JsonObject o) {
        if (o == null) {
            return null;
        }
        return new Period(str(o, "id"), intVal(o, "period", -1), str(o, "startTime"), str(o, "endTime"));
    }

    public static DayDefinition day(JsonObject o) {
        if (o == null) {
            return null;
        }
        return new DayDefinition(str(o, "id"), str(o, "name"), str(o, "short"), strings(o, "days"));
    }

    public static WeekDefinition week(JsonObject o) {
        if (o == null) {
            return null;
        }
        return new WeekDefinition(str(o, "id"), str(o, "name"), str(o, "short"), strings(o, "weeks"));
    }

    public static Lesson lesson(JsonObject o) {
        if (o == null) {
            return null;
        }
        return new Lesson(
                str(o, "id"),
                namedRef(obj(o, "subject")),
                mapChildArray(o, "teachers", FilcJson::namedRef),
                mapChildArray(o, "classrooms", FilcJson::namedRef),
                mapChildArray(o, "cohorts", FilcJson::namedRef),
                mapChildArray(o, "groups", FilcJson::group),
                period(obj(o, "period")),
                day(obj(o, "day")),
                week(obj(o, "weekDefinition")));
    }

    public static Substitution substitution(JsonObject o) {
        if (o == null) {
            return null;
        }
        return new Substitution(
                str(o, "id"),
                parseDate(str(o, "date")),
                str(o, "comment"),
                str(o, "substituter"));
    }

    /**
     * Parses the ISO instant the API sends for substitution dates. Only the date
     * part is meaningful; the time is always midnight UTC.
     */
    public static LocalDate parseDate(String raw) {
        if (raw == null || raw.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(raw.substring(0, 10));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static SubstitutionEntry substitutionEntry(JsonObject o) {
        if (o == null) {
            return null;
        }
        List<String> lessonIds = new ArrayList<>();
        if (o.has("lessons") && o.get("lessons").isJsonArray()) {
            for (JsonElement el : o.getAsJsonArray("lessons")) {
                if (el.isJsonNull()) {
                    continue;
                }
                // The cohort-scoped endpoint sends bare ids; the global one sends
                // whole lesson objects. Accept either.
                if (el.isJsonPrimitive()) {
                    lessonIds.add(el.getAsString());
                } else if (el.isJsonObject()) {
                    String id = str(el.getAsJsonObject(), "id");
                    if (id != null) {
                        lessonIds.add(id);
                    }
                }
            }
        }
        return new SubstitutionEntry(
                substitution(obj(o, "substitution")),
                teacher(obj(o, "teacher")),
                Collections.unmodifiableList(lessonIds));
    }

    /** Reads the {cohortId, substitutions:[...]} payload of the cohort endpoint. */
    public static List<SubstitutionEntry> cohortSubstitutions(JsonElement data) {
        if (data == null || !data.isJsonObject()) {
            return List.of();
        }
        return mapChildArray(data.getAsJsonObject(), "substitutions", FilcJson::substitutionEntry);
    }
}
