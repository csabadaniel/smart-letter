package com.smartletter.settings.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartletter.settings.config.DeliveryConfigurationProperties;
import com.smartletter.settings.firestore.DeliveryConfigurationDocument;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeliveryConfigurationCacheTest {

    private static final DeliveryConfigurationDocument DOCUMENT = new DeliveryConfigurationDocument(
            "ops@example.com",
            "Prompt",
            "hash-value",
            3,
            "ops:local",
            Instant.parse("2025-11-30T00:00:00Z"));

    private DeliveryConfigurationCache cache;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        DeliveryConfigurationProperties properties = new DeliveryConfigurationProperties();
        properties.setCollectionPath("appSettings/configuration/delivery/main");
        properties.setCacheTtlSeconds(5);
        clock = new MutableClock(Instant.parse("2025-11-30T00:00:00Z"));
        cache = new DeliveryConfigurationCache(properties, clock);
    }

    @Test
    void getReturnsEmptyWhenCacheUnused() {
        assertThat(cache.get()).isEmpty();
    }

    @Test
    void cacheReturnsValueUntilTtlExpires() {
        cache.cache(DOCUMENT);

        assertThat(cache.get())
                .isPresent()
                .get()
                .satisfies(snapshot -> {
                    assertThat(snapshot.document()).isEqualTo(DOCUMENT);
                    assertThat(snapshot.etag()).isEqualTo("\"3-hash-value\"");
                });

        clock.advance(Duration.ofSeconds(4));
        assertThat(cache.get()).isPresent();

        clock.advance(Duration.ofSeconds(2));
        assertThat(cache.get()).isEmpty();
    }

    @Test
    void invalidateClearsCacheImmediately() {
        cache.cache(DOCUMENT);

        cache.invalidate();

        assertThat(cache.get()).isEmpty();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;
        private final ZoneId zone = ZoneId.of("UTC");

        private MutableClock(Instant initialInstant) {
            this.instant = initialInstant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
