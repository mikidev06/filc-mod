package me.mikidev06.filc_mod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The handful of settings the mod remembers between sessions.
 *
 * <p>Deliberately a plain JSON file rather than a config library: there are
 * three values and adding a dependency for them would not pay for itself.
 */
public final class FilcConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String defaultCohort;
    private String defaultGroup;
    private String baseUrl;

    public FilcConfig() {
        this.baseUrl = me.mikidev06.filc_mod.api.FilcClient.DEFAULT_BASE_URL;
    }

    public String defaultCohort() {
        return defaultCohort;
    }

    public void setDefaultCohort(String defaultCohort) {
        this.defaultCohort = defaultCohort;
    }

    public String defaultGroup() {
        return defaultGroup;
    }

    public void setDefaultGroup(String defaultGroup) {
        this.defaultGroup = defaultGroup;
    }

    public String baseUrl() {
        return baseUrl == null || baseUrl.isBlank()
                ? me.mikidev06.filc_mod.api.FilcClient.DEFAULT_BASE_URL
                : baseUrl;
    }

    public boolean hasDefaultCohort() {
        return defaultCohort != null && !defaultCohort.isBlank();
    }

    public static FilcConfig load(Path file) {
        FilcConfig config = new FilcConfig();
        if (file == null || !Files.isRegularFile(file)) {
            return config;
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            if (root.has("defaultCohort") && !root.get("defaultCohort").isJsonNull()) {
                config.defaultCohort = root.get("defaultCohort").getAsString();
            }
            if (root.has("defaultGroup") && !root.get("defaultGroup").isJsonNull()) {
                config.defaultGroup = root.get("defaultGroup").getAsString();
            }
            if (root.has("baseUrl") && !root.get("baseUrl").isJsonNull()) {
                config.baseUrl = root.get("baseUrl").getAsString();
            }
        } catch (IOException | RuntimeException e) {
            // A damaged config should not stop the game loading; defaults are fine.
            return new FilcConfig();
        }
        return config;
    }

    public void save(Path file) {
        if (file == null) {
            return;
        }
        JsonObject root = new JsonObject();
        if (defaultCohort != null) {
            root.addProperty("defaultCohort", defaultCohort);
        }
        if (defaultGroup != null) {
            root.addProperty("defaultGroup", defaultGroup);
        }
        root.addProperty("baseUrl", baseUrl());
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Losing a preference is not worth crashing a command over.
        }
    }
}
