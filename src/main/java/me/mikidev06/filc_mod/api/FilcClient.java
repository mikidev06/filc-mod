package me.mikidev06.filc_mod.api;

import com.google.gson.JsonElement;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import me.mikidev06.filc_mod.cache.ResourceCache;
import me.mikidev06.filc_mod.model.Classroom;
import me.mikidev06.filc_mod.model.Cohort;
import me.mikidev06.filc_mod.model.Group;
import me.mikidev06.filc_mod.model.Lesson;
import me.mikidev06.filc_mod.model.Period;
import me.mikidev06.filc_mod.model.SubstitutionEntry;
import me.mikidev06.filc_mod.model.Teacher;
import me.mikidev06.filc_mod.model.Timetable;

/**
 * Reads the public Chronos API behind filc.petrik.hu.
 *
 * <p>Every call is asynchronous and cache-backed. Identical requests issued
 * while one is already in flight share that result rather than starting a
 * second one, which matters because opening a timetable fans out into five
 * requests at once.
 *
 * <p>None of the endpoints used here require authentication.
 */
public final class FilcClient {

    public static final String DEFAULT_BASE_URL = "https://filc.petrik.hu/api";

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final long MAX_RETRY_DELAY_SECONDS = 30;

    private final String baseUrl;
    private final ResourceCache cache;
    private final HttpClient http;
    private final ScheduledExecutorService scheduler;
    private final Map<String, CompletableFuture<String>> inFlight = new ConcurrentHashMap<>();

    public FilcClient(String baseUrl, ResourceCache cache) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.cache = cache;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "filc-retry");
            t.setDaemon(true);
            return t;
        });
        Executor pool = Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r, "filc-http");
            t.setDaemon(true);
            return t;
        });
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .executor(pool)
                .build();
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public ResourceCache cache() {
        return cache;
    }

    // ---- typed endpoints -------------------------------------------------

    public CompletableFuture<Timetable> latestValidTimetable() {
        return get("/timetable/timetables/latestValid", Ttl.TIMETABLE)
                .thenApply(data -> data.isJsonObject() ? FilcJson.timetable(data.getAsJsonObject()) : null);
    }

    public CompletableFuture<List<Cohort>> cohorts(String timetableId) {
        return get("/timetable/cohorts/getAllForTimetable/" + encode(timetableId), Ttl.REFERENCE)
                .thenApply(data -> FilcJson.mapArray(data, FilcJson::cohort));
    }

    public CompletableFuture<List<Period>> periods(String timetableId) {
        return get("/timetable/periods/getAll?timetableId=" + encode(timetableId), Ttl.REFERENCE)
                .thenApply(data -> FilcJson.mapArray(data, FilcJson::period));
    }

    public CompletableFuture<List<Teacher>> teachers() {
        return get("/timetable/teachers/getAll", Ttl.REFERENCE)
                .thenApply(data -> FilcJson.mapArray(data, FilcJson::teacher));
    }

    public CompletableFuture<List<Classroom>> classrooms() {
        return get("/timetable/classrooms/getAll", Ttl.REFERENCE)
                .thenApply(data -> FilcJson.mapArray(data, FilcJson::classroom));
    }

    public CompletableFuture<List<Group>> groups(String cohortId) {
        return get("/timetable/groups/getForCohort/" + encode(cohortId), Ttl.REFERENCE)
                .thenApply(data -> FilcJson.mapArray(data, FilcJson::group));
    }

    public CompletableFuture<List<Lesson>> lessonsForCohort(String cohortId, String timetableId) {
        return lessons("/timetable/lessons/getForCohort/" + encode(cohortId), timetableId);
    }

    public CompletableFuture<List<Lesson>> lessonsForTeacher(String teacherId, String timetableId) {
        return lessons("/timetable/lessons/getForTeacher/" + encode(teacherId), timetableId);
    }

    public CompletableFuture<List<Lesson>> lessonsForRoom(String classroomId, String timetableId) {
        return lessons("/timetable/lessons/getForRoom/" + encode(classroomId), timetableId);
    }

    private CompletableFuture<List<Lesson>> lessons(String path, String timetableId) {
        String full = timetableId == null ? path : path + "?timetableId=" + encode(timetableId);
        return get(full, Ttl.LESSONS).thenApply(data -> FilcJson.mapArray(data, FilcJson::lesson));
    }

    public CompletableFuture<List<SubstitutionEntry>> substitutionsForCohort(String cohortId) {
        return get("/timetable/substitutions/cohort/" + encode(cohortId), Ttl.SUBSTITUTIONS)
                .thenApply(FilcJson::cohortSubstitutions);
    }

    // ---- plumbing --------------------------------------------------------

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Fetches a path, unwrapping the success envelope. */
    public CompletableFuture<JsonElement> get(String path, Duration ttl) {
        return fetchRaw(path, ttl).thenApply(body -> FilcJson.unwrap(body, 200, -1));
    }

    private CompletableFuture<String> fetchRaw(String path, Duration ttl) {
        Optional<String> cached = cache.get(path, ttl, Instant.now());
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached.get());
        }
        // Share one request between concurrent callers asking for the same path.
        return inFlight.computeIfAbsent(path, key -> send(key, 0)
                .whenComplete((body, error) -> inFlight.remove(key)));
    }

    private CompletableFuture<String> send(String path, int attempt) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "filc-mod (Minecraft Fabric client)")
                .GET()
                .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenCompose(response -> {
                    int status = response.statusCode();
                    if (status == 429 && attempt == 0) {
                        long delay = retryAfter(response).orElse(5L);
                        return retryAfterDelay(path, Math.min(delay, MAX_RETRY_DELAY_SECONDS));
                    }
                    if (status >= 400) {
                        // Let the envelope parser turn the body into a typed error;
                        // it carries the machine-readable code.
                        try {
                            FilcJson.unwrap(response.body(), status, retryAfter(response).orElse(-1L));
                        } catch (FilcApiException e) {
                            return CompletableFuture.failedFuture(e);
                        }
                    }
                    String body = response.body();
                    cache.put(path, body, Instant.now());
                    return CompletableFuture.completedFuture(body);
                });
    }

    private CompletableFuture<String> retryAfterDelay(String path, long seconds) {
        CompletableFuture<String> delayed = new CompletableFuture<>();
        scheduler.schedule(
                () -> send(path, 1).whenComplete((body, error) -> {
                    if (error != null) {
                        delayed.completeExceptionally(error);
                    } else {
                        delayed.complete(body);
                    }
                }),
                seconds,
                TimeUnit.SECONDS);
        return delayed;
    }

    private static Optional<Long> retryAfter(HttpResponse<?> response) {
        return response.headers().firstValue("Retry-After").flatMap(raw -> {
            try {
                return Optional.of(Long.parseLong(raw.trim()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    /** Unwraps the cause a CompletableFuture wraps failures in. */
    public static Throwable unwrapCause(Throwable error) {
        Throwable current = error;
        while (current instanceof java.util.concurrent.CompletionException
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /** A short, human-readable reason for a failed request. */
    public static String describe(Throwable error) {
        Throwable cause = unwrapCause(error);
        if (cause instanceof FilcApiException api) {
            if (api.isRateLimited()) {
                long wait = api.retryAfterSeconds();
                return wait > 0
                        ? "Filc rate limited the request; try again in " + wait + "s"
                        : "Filc rate limited the request; try again shortly";
            }
            return api.getMessage();
        }
        String message = cause.getMessage();
        return message == null ? cause.getClass().getSimpleName() : message;
    }
}
