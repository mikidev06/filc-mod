package me.mikidev06.filc_mod.cache;

import java.time.Duration;
import java.time.Instant;

/** A stored response body together with the moment it was fetched. */
public record CachedBody(String body, Instant fetchedAt) {
    public boolean isFresh(Instant now, Duration ttl) {
        return fetchedAt != null && now.isBefore(fetchedAt.plus(ttl));
    }
}
