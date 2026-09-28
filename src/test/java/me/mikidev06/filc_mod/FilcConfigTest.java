package me.mikidev06.filc_mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import me.mikidev06.filc_mod.api.FilcClient;
import me.mikidev06.filc_mod.config.FilcConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilcConfigTest {

    @Test
    void defaultsToThePublicApiAndNoClass() {
        FilcConfig config = new FilcConfig();
        assertEquals(FilcClient.DEFAULT_BASE_URL, config.baseUrl());
        assertFalse(config.hasDefaultCohort());
        assertNull(config.defaultCohort());
    }

    @Test
    void roundTripsThroughAFile(@TempDir Path dir) {
        Path file = dir.resolve("nested").resolve("config.json");
        FilcConfig config = new FilcConfig();
        config.setDefaultCohort("9.A");
        config.setDefaultGroup("info2");
        config.save(file);

        FilcConfig loaded = FilcConfig.load(file);
        assertEquals("9.A", loaded.defaultCohort());
        assertEquals("info2", loaded.defaultGroup());
        assertTrue(loaded.hasDefaultCohort());
    }

    @Test
    void savingCreatesMissingDirectories(@TempDir Path dir) {
        Path file = dir.resolve("a").resolve("b").resolve("config.json");
        new FilcConfig().save(file);
        assertTrue(Files.isRegularFile(file));
    }

    @Test
    void loadingAMissingFileGivesDefaults(@TempDir Path dir) {
        FilcConfig config = FilcConfig.load(dir.resolve("absent.json"));
        assertFalse(config.hasDefaultCohort());
        assertEquals(FilcClient.DEFAULT_BASE_URL, config.baseUrl());
    }

    @Test
    void aDamagedFileFallsBackToDefaultsRatherThanThrowing(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.json");
        Files.writeString(file, "{ this is not json");
        FilcConfig config = FilcConfig.load(file);
        assertFalse(config.hasDefaultCohort());
        assertEquals(FilcClient.DEFAULT_BASE_URL, config.baseUrl());
    }

    @Test
    void aBlankBaseUrlFallsBackToTheDefault(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.json");
        Files.writeString(file, "{\"baseUrl\":\"\"}");
        assertEquals(FilcClient.DEFAULT_BASE_URL, FilcConfig.load(file).baseUrl());
    }

    @Test
    void anOmittedGroupStaysNull(@TempDir Path dir) {
        Path file = dir.resolve("config.json");
        FilcConfig config = new FilcConfig();
        config.setDefaultCohort("12.C");
        config.save(file);
        assertNull(FilcConfig.load(file).defaultGroup());
    }
}
