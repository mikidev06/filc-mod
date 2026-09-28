package me.mikidev06.filc_mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import me.mikidev06.filc_mod.api.FilcApiException;
import me.mikidev06.filc_mod.api.FilcJson;
import me.mikidev06.filc_mod.model.Classroom;
import me.mikidev06.filc_mod.model.Cohort;
import me.mikidev06.filc_mod.model.Group;
import me.mikidev06.filc_mod.model.Lesson;
import me.mikidev06.filc_mod.model.Period;
import me.mikidev06.filc_mod.model.SubstitutionEntry;
import me.mikidev06.filc_mod.model.Teacher;
import me.mikidev06.filc_mod.model.Timetable;
import org.junit.jupiter.api.Test;

class FilcJsonTest {

    @Test
    void unwrapsTheSuccessEnvelope() {
        Timetable timetable = FilcJson.timetable(
                Fixtures.data("latest-valid.json").getAsJsonObject());
        assertEquals("04a56d83-3603-4496-8cb1-bf4c696137d8", timetable.id());
        assertEquals("2026-09-24", timetable.validFrom());
        assertNull(timetable.validTo(), "the current timetable has no end date");
    }

    @Test
    void reportsErrorEnvelopesAsExceptions() {
        FilcApiException error = assertThrows(FilcApiException.class,
                () -> FilcJson.unwrap(Fixtures.raw("error-rate-limited.json"), 429, 12));
        assertEquals("RATE_LIMITED", error.code());
        assertEquals(429, error.statusCode());
        assertEquals(12, error.retryAfterSeconds());
        assertTrue(error.isRateLimited());
        assertEquals("Too many requests", error.getMessage());
    }

    @Test
    void treatsAnyFourHundredAsAnError() {
        // A body can claim success while the status says otherwise; the status wins.
        assertThrows(FilcApiException.class,
                () -> FilcJson.unwrap("{\"data\":null,\"success\":true}", 500, -1));
    }

    @Test
    void rejectsBodiesThatAreNotJson() {
        assertThrows(FilcApiException.class, () -> FilcJson.unwrap("<html>nope</html>", 200, -1));
    }

    @Test
    void parsesCohorts() {
        List<Cohort> cohorts = FilcJson.mapArray(Fixtures.data("cohorts.json"), FilcJson::cohort);
        assertFalse(cohorts.isEmpty());
        Cohort ninthA = cohorts.stream()
                .filter(c -> "9.A".equals(c.shortName()))
                .findFirst()
                .orElseThrow();
        assertEquals("40a646d9-e896-49c5-8018-c45ccc2c685f", ninthA.id());
        assertEquals("9.A", ninthA.display());
    }

    @Test
    void parsesPeriodsAndTrimsTheSeconds() {
        List<Period> periods = FilcJson.mapArray(Fixtures.data("periods.json"), FilcJson::period);
        Period first = periods.stream().filter(p -> p.number() == 1).findFirst().orElseThrow();
        assertEquals("08:00", first.startShort());
        assertEquals("08:45", first.endShort());
    }

    @Test
    void parsesTeachersInHungarianNameOrder() {
        List<Teacher> teachers = FilcJson.mapArray(Fixtures.data("teachers.json"), FilcJson::teacher);
        Teacher teacher = teachers.stream()
                .filter(t -> "Bozóki".equals(t.firstName()))
                .findFirst()
                .orElseThrow();
        // The API puts the family name in firstName; joining must not reorder it.
        assertEquals("Bozóki Judit", teacher.displayName());
    }

    @Test
    void parsesClassrooms() {
        List<Classroom> rooms = FilcJson.mapArray(Fixtures.data("classrooms.json"), FilcJson::classroom);
        assertFalse(rooms.isEmpty());
        assertTrue(rooms.stream().anyMatch(r -> "A118".equals(r.shortName())));
    }

    @Test
    void parsesLessonsWithTheirNestedRelations() {
        List<Lesson> lessons = FilcJson.mapArray(Fixtures.data("lessons-9a.json"), FilcJson::lesson);
        assertEquals(68, lessons.size());

        Lesson lesson = lessons.stream()
                .filter(l -> l.dayNumber() == 1 && l.periodNumber() == 4)
                .filter(l -> "Mészáros Tamás".equals(l.teacherNames()))
                .findFirst()
                .orElseThrow();

        assertEquals("DiKu", lesson.subjectShort());
        assertEquals("A118", lesson.roomNames());
        assertEquals("info1", lesson.groupLabel());
        assertFalse(lesson.isWholeClass());
        assertEquals("", lesson.weekLabel(), "a weekly lesson needs no A/B marker");
    }

    @Test
    void marksAlternatingWeekLessons() {
        List<Lesson> lessons = FilcJson.mapArray(Fixtures.data("lessons-9a.json"), FilcJson::lesson);
        List<String> labels = lessons.stream()
                .map(Lesson::weekLabel)
                .filter(label -> !label.isEmpty())
                .sorted()
                .toList();
        assertEquals(List.of("A", "B"), labels);
    }

    @Test
    void parsesGroupsIncludingTheWholeClassEntry() {
        List<Group> groups = FilcJson.mapArray(Fixtures.data("groups-9a.json"), FilcJson::group);
        assertTrue(groups.stream().anyMatch(Group::entireClass));
        assertTrue(groups.stream().anyMatch(g -> "gy1".equals(g.name())));
    }

    @Test
    void parsesCohortSubstitutionsWhoseLessonsAreBareIds() {
        List<SubstitutionEntry> entries =
                FilcJson.cohortSubstitutions(Fixtures.data("subs-9a.json"));
        assertEquals(1, entries.size());

        SubstitutionEntry entry = entries.get(0);
        assertEquals(LocalDate.of(2026, 10, 2), entry.substitution().date());
        assertEquals("Lukács Ferenc", entry.teacher().displayName());
        assertEquals(List.of("85717811-1a1e-44c8-943a-c571c5ed3ea1"), entry.lessonIds());
        assertNull(entry.substitution().comment());
    }

    @Test
    void readsDatesFromTheIsoInstantTheApiSends() {
        assertEquals(LocalDate.of(2026, 10, 2), FilcJson.parseDate("2026-10-02T00:00:00.000Z"));
        assertNull(FilcJson.parseDate(null));
        assertNull(FilcJson.parseDate("nonsense"));
    }

    @Test
    void survivesMissingAndNullFields() {
        assertNull(FilcJson.lesson(null));
        Lesson bare = FilcJson.lesson(
                com.google.gson.JsonParser.parseString("{\"id\":\"x\"}").getAsJsonObject());
        assertNotNull(bare);
        assertEquals("?", bare.subjectShort());
        assertEquals("", bare.teacherNames());
        assertEquals(-1, bare.dayNumber());
        assertEquals(-1, bare.periodNumber());
        assertTrue(bare.isWholeClass());
    }

    @Test
    void returnsJsonNullWhenAResponseCarriesNoData() {
        assertSame(com.google.gson.JsonNull.INSTANCE,
                FilcJson.unwrap("{\"success\":true}", 200, -1));
    }
}
