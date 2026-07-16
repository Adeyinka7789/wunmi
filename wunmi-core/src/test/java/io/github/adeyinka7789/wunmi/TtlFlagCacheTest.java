package io.github.adeyinka7789.wunmi;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TtlFlagCacheTest {

    /** A clock the test can advance by hand. */
    private static final class MutableClock extends Clock {
        private long millis;
        MutableClock(long start) { this.millis = start; }
        void advance(long delta) { this.millis += delta; }
        @Override public long millis() { return millis; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    private static Map<String, Flag> oneFlag() {
        return Map.of("F", Flag.enabledFlag("F"));
    }

    @Test
    void withinTtl_doesNotReload() {
        MutableClock clock = new MutableClock(0);
        TtlFlagCache cache = new TtlFlagCache(5000, clock);
        AtomicInteger loads = new AtomicInteger();

        cache.flags(() -> { loads.incrementAndGet(); return oneFlag(); });
        clock.advance(4999);
        cache.flags(() -> { loads.incrementAndGet(); return oneFlag(); });

        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    void afterTtl_reloads() {
        MutableClock clock = new MutableClock(0);
        TtlFlagCache cache = new TtlFlagCache(5000, clock);
        AtomicInteger loads = new AtomicInteger();

        cache.flags(() -> { loads.incrementAndGet(); return oneFlag(); });
        clock.advance(5000);
        cache.flags(() -> { loads.incrementAndGet(); return oneFlag(); });

        assertThat(loads.get()).isEqualTo(2);
    }

    @Test
    void override_memoizedByKeyWithinTtl() {
        TtlFlagCache cache = new TtlFlagCache(5000, new MutableClock(0));
        AtomicInteger loads = new AtomicInteger();

        Optional<FlagOverride> first = cache.override("k", () -> {
            loads.incrementAndGet();
            return Optional.of(FlagOverride.create("F", FlagOverride.Scope.SUBJECT, "u", true, null, "a"));
        });
        cache.override("k", () -> {
            loads.incrementAndGet();
            return Optional.of(FlagOverride.create("F", FlagOverride.Scope.SUBJECT, "u", false, null, "a"));
        });

        assertThat(loads.get()).isEqualTo(1);
        assertThat(first).isPresent();
    }

    @Test
    void noneCache_alwaysCallsLoader() {
        AtomicInteger loads = new AtomicInteger();
        FlagCache.NONE.flags(() -> { loads.incrementAndGet(); return oneFlag(); });
        FlagCache.NONE.flags(() -> { loads.incrementAndGet(); return oneFlag(); });
        assertThat(loads.get()).isEqualTo(2);
    }
}
