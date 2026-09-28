package me.mikidev06.filc_mod.client;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import me.mikidev06.filc_mod.api.FilcClient;
import me.mikidev06.filc_mod.cache.ResourceCache;
import me.mikidev06.filc_mod.config.FilcConfig;
import me.mikidev06.filc_mod.model.Classroom;
import me.mikidev06.filc_mod.model.Cohort;
import me.mikidev06.filc_mod.model.Teacher;
import me.mikidev06.filc_mod.model.Timetable;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/**
 * Process-wide state for the mod: the API client, the saved config, and the
 * reference lists that command completion reads from.
 *
 * <p>Completion has to answer synchronously while the player is still typing,
 * so the cohort, teacher and room lists are mirrored into plain fields as soon
 * as anything fetches them. Before the first fetch completion simply offers
 * nothing, which is better than blocking the client thread on a network call.
 */
public final class FilcState {

    private static FilcState instance;

    private final FilcConfig config;
    private final Path configFile;
    private final FilcClient client;

    private final AtomicReference<Timetable> timetable = new AtomicReference<>();
    private final AtomicReference<List<Cohort>> cohorts = new AtomicReference<>(List.of());
    private final AtomicReference<List<Teacher>> teachers = new AtomicReference<>(List.of());
    private final AtomicReference<List<Classroom>> classrooms = new AtomicReference<>(List.of());

    /**
     * A screen waiting to be shown. Screens cannot be opened from inside a
     * command: the chat screen closes itself afterwards and would immediately
     * replace whatever the command set. Opening on the next client tick avoids
     * that, and also lets an async fetch hand a screen over from its own thread.
     */
    private final AtomicReference<Screen> pendingScreen = new AtomicReference<>();

    private FilcState() {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("filc_mod");
        this.configFile = dir.resolve("config.json");
        this.config = FilcConfig.load(configFile);
        this.client = new FilcClient(config.baseUrl(), new ResourceCache(dir.resolve("cache")));
    }

    public static synchronized FilcState get() {
        if (instance == null) {
            instance = new FilcState();
        }
        return instance;
    }

    public FilcClient client() {
        return client;
    }

    public FilcConfig config() {
        return config;
    }

    public void saveConfig() {
        config.save(configFile);
    }

    public List<Cohort> cohorts() {
        return cohorts.get();
    }

    public List<Teacher> teachers() {
        return teachers.get();
    }

    public List<Classroom> classrooms() {
        return classrooms.get();
    }

    public Timetable timetable() {
        return timetable.get();
    }

    /** Resolves the current timetable, remembering it for completion. */
    public CompletableFuture<Timetable> currentTimetable() {
        Timetable known = timetable.get();
        if (known != null) {
            return CompletableFuture.completedFuture(known);
        }
        return client.latestValidTimetable().thenApply(fetched -> {
            timetable.set(fetched);
            return fetched;
        });
    }

    public CompletableFuture<List<Cohort>> loadCohorts() {
        return currentTimetable().thenCompose(tt -> {
            if (tt == null) {
                return CompletableFuture.completedFuture(List.<Cohort>of());
            }
            return client.cohorts(tt.id());
        }).thenApply(list -> {
            cohorts.set(list);
            return list;
        });
    }

    public CompletableFuture<List<Teacher>> loadTeachers() {
        return client.teachers().thenApply(list -> {
            teachers.set(list);
            return list;
        });
    }

    public CompletableFuture<List<Classroom>> loadClassrooms() {
        return client.classrooms().thenApply(list -> {
            classrooms.set(list);
            return list;
        });
    }

    /** Queues a screen to be opened on the next client tick. */
    public void openLater(Screen screen) {
        pendingScreen.set(screen);
    }

    /** Called once per client tick; shows whatever screen is waiting. */
    public void drainPendingScreen(MinecraftClient minecraft) {
        Screen screen = pendingScreen.getAndSet(null);
        if (screen != null) {
            minecraft.setScreen(screen);
        }
    }

    /** Drops every cached response and the remembered reference lists. */
    public void clearCaches() {
        client.cache().clear();
        timetable.set(null);
        cohorts.set(List.of());
        teachers.set(List.of());
        classrooms.set(List.of());
    }
}
