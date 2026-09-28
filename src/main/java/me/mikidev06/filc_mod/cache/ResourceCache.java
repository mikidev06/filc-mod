package me.mikidev06.filc_mod.cache;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches raw response bodies by request key.
 *
 * <p>Raw bodies rather than parsed objects, so entries can be written to disk
 * and survive a restart without a serialisation layer. The API allows 180
 * requests per 30 seconds per address, and a single class timetable is 84 KB,
 * so re-fetching per command is not an option.
 *
 * <p>A null cacheDir disables disk persistence, which is what the tests use.
 */
public final class ResourceCache {

    private final Map<String, CachedBody> memory = new ConcurrentHashMap<>();
    private final Path cacheDir;

    public ResourceCache(Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    /** Returns the stored body when one exists and is still within its TTL. */
    public Optional<String> get(String key, Duration ttl, Instant now) {
        CachedBody entry = memory.get(key);
        if (entry == null) {
            entry = readFromDisk(key);
            if (entry != null) {
                memory.put(key, entry);
            }
        }
        if (entry != null && entry.isFresh(now, ttl)) {
            return Optional.of(entry.body());
        }
        return Optional.empty();
    }

    public void put(String key, String body, Instant now) {
        CachedBody entry = new CachedBody(body, now);
        memory.put(key, entry);
        writeToDisk(key, entry);
    }

    /** Drops everything, in memory and on disk. Backs /filc refresh. */
    public void clear() {
        memory.clear();
        if (cacheDir == null || !Files.isDirectory(cacheDir)) {
            return;
        }
        try (var stream = Files.list(cacheDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".cache"))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // A file we cannot delete is stale at worst; the TTL
                            // check still rejects it.
                        }
                    });
        } catch (IOException ignored) {
            // Nothing we can do, and failing to clear a cache must not break the
            // command that asked for it.
        }
    }

    public int size() {
        return memory.size();
    }

    // ---- disk ------------------------------------------------------------

    private Path fileFor(String key) {
        return cacheDir.resolve(hash(key) + ".cache");
    }

    private CachedBody readFromDisk(String key) {
        if (cacheDir == null) {
            return null;
        }
        Path file = fileFor(key);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            int split = raw.indexOf('\n');
            if (split < 0) {
                return null;
            }
            Instant fetchedAt = Instant.parse(raw.substring(0, split));
            return new CachedBody(raw.substring(split + 1), fetchedAt);
        } catch (IOException | RuntimeException e) {
            // A corrupt entry is indistinguishable from a miss as far as callers
            // are concerned.
            return null;
        }
    }

    private void writeToDisk(String key, CachedBody entry) {
        if (cacheDir == null) {
            return;
        }
        try {
            Files.createDirectories(cacheDir);
            Files.writeString(
                    fileFor(key),
                    entry.fetchedAt().toString() + "\n" + entry.body(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write Filc cache entry", e);
        }
    }

    static String hash(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] out = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is required by the JDK", e);
        }
    }
}
