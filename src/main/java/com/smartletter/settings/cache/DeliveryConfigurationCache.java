package com.smartletter.settings.cache;

import com.smartletter.settings.config.DeliveryConfigurationProperties;
import com.smartletter.settings.firestore.DeliveryConfigurationDocument;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Simple in-memory cache for the singleton delivery configuration document with TTL + ETag helpers.
 */
@Component
public class DeliveryConfigurationCache {

    private final Duration ttl;
    private final Clock clock;
    private final AtomicReference<CacheEntry> cache = new AtomicReference<>();

    public DeliveryConfigurationCache(DeliveryConfigurationProperties properties, Clock clock) {
        Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.ttl = Duration.ofSeconds(properties.getCacheTtlSeconds());
    }

    public Optional<CacheSnapshot> get() {
        CacheEntry entry = cache.get();
        if (entry == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        if (now.isAfter(entry.expiresAt())) {
            cache.compareAndSet(entry, null);
            return Optional.empty();
        }
        return Optional.of(new CacheSnapshot(entry.document(), entry.etag()));
    }

    public void cache(DeliveryConfigurationDocument document) {
        Objects.requireNonNull(document, "document must not be null");
        Instant now = clock.instant();
        cache.set(new CacheEntry(document, now.plus(ttl), buildEtag(document)));
    }

    public void invalidate() {
        cache.set(null);
    }

    private String buildEtag(DeliveryConfigurationDocument document) {
        return "\"" + document.version() + "-" + document.promptSha256() + "\"";
    }

    public record CacheSnapshot(DeliveryConfigurationDocument document, String etag) {}

    private record CacheEntry(DeliveryConfigurationDocument document, Instant expiresAt, String etag) {}
}
