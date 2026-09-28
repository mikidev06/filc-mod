package me.mikidev06.filc_mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import me.mikidev06.filc_mod.cache.ResourceCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourceCacheTest {

    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final Instant NOW = Instant.parse("2026-09-28T10:00:00Z");

    @Test
    void returnsNothingForAKeyItHasNeverSeen() {
        ResourceCache cache = new ResourceCache(null);
        assertTrue(cache.get("/timetable/timetables", TEN_MINUTES, NOW).isEmpty());
    }

    @Test
    void returnsAStoredBodyWhileItIsFresh() {
        ResourceCache cache = new ResourceCache(null);
        cache.put("/a", "{\"ok\":1}", NOW);
        assertEquals(Optional.of("{\"ok\":1}"), cache.get("/a", TEN_MINUTES, NOW.plusSeconds(599)));
    }

    @Test
    void expiresAStoredBodyOnceTheTtlHasPassed() {
        ResourceCache cache = new ResourceCache(null);
        cache.put("/a", "body", NOW);
        assertTrue(cache.get("/a", TEN_MINUTES, NOW.plusSeconds(600)).isEmpty(),
                "the entry is stale exactly at the TTL boundary");
        assertTrue(cache.get("/a", TEN_MINUTES, NOW.plusSeconds(6000)).isEmpty());
    }

    @Test
    void keysAreIndependent() {
        ResourceCache cache = new ResourceCache(null);
        cache.put("/a", "first", NOW);
        cache.put("/b", "second", NOW);
        assertEquals(Optional.of("first"), cache.get("/a", TEN_MINUTES, NOW));
        assertEquals(Optional.of("second"), cache.get("/b", TEN_MINUTES, NOW));
    }

    @Test
    void survivesARestartByReadingFromDisk(@TempDir Path dir) {
        new ResourceCache(dir).put("/timetable/teachers/getAll", "[1,2,3]", NOW);

        ResourceCache reopened = new ResourceCache(dir);
        assertEquals(Optional.of("[1,2,3]"),
                reopened.get("/timetable/teachers/getAll", TEN_MINUTES, NOW.plusSeconds(60)));
    }

    @Test
    void discardsDiskEntriesThatHaveExpired(@TempDir Path dir) {
        new ResourceCache(dir).put("/a", "body", NOW);
        assertTrue(new ResourceCache(dir).get("/a", TEN_MINUTES, NOW.plusSeconds(3600)).isEmpty());
    }

    @Test
    void roundTripsBodiesContainingNewlines(@TempDir Path dir) {
        // The on-disk format puts the timestamp on the first line, so a body with
        // its own newlines must not confuse the split.
        String body = "{\n  \"a\": 1,\n  \"b\": 2\n}";
        new ResourceCache(dir).put("/pretty", body, NOW);
        assertEquals(Optional.of(body), new ResourceCache(dir).get("/pretty", TEN_MINUTES, NOW));
    }

    @Test
    void treatsACorruptDiskEntryAsAMiss(@TempDir Path dir) throws IOException {
        ResourceCache cache = new ResourceCache(dir);
        cache.put("/a", "body", NOW);
        Path file = Files.list(dir).filter(p -> p.toString().endsWith(".cache")).findFirst().orElseThrow();
        Files.writeString(file, "this is not a timestamp");

        assertTrue(new ResourceCache(dir).get("/a", TEN_MINUTES, NOW).isEmpty());
    }

    @Test
    void clearDropsMemoryAndDisk(@TempDir Path dir) throws IOException {
        ResourceCache cache = new ResourceCache(dir);
        cache.put("/a", "body", NOW);
        cache.put("/b", "body", NOW);
        assertEquals(2, cache.size());

        cache.clear();

        assertEquals(0, cache.size());
        assertTrue(cache.get("/a", TEN_MINUTES, NOW).isEmpty());
        assertFalse(Files.list(dir).anyMatch(p -> p.toString().endsWith(".cache")));
        assertTrue(new ResourceCache(dir).get("/a", TEN_MINUTES, NOW).isEmpty());
    }

    @Test
    void writingTwiceReplacesTheEntry() {
        ResourceCache cache = new ResourceCache(null);
        cache.put("/a", "old", NOW);
        cache.put("/a", "new", NOW.plusSeconds(1));
        assertEquals(Optional.of("new"), cache.get("/a", TEN_MINUTES, NOW.plusSeconds(2)));
    }

    @Test
    void queryStringsProduceDistinctFilenames(@TempDir Path dir) {
        ResourceCache cache = new ResourceCache(dir);
        cache.put("/periods/getAll?timetableId=a", "A", NOW);
        cache.put("/periods/getAll?timetableId=b", "B", NOW);
        ResourceCache reopened = new ResourceCache(dir);
        assertEquals(Optional.of("A"), reopened.get("/periods/getAll?timetableId=a", TEN_MINUTES, NOW));
        assertEquals(Optional.of("B"), reopened.get("/periods/getAll?timetableId=b", TEN_MINUTES, NOW));
    }
}
