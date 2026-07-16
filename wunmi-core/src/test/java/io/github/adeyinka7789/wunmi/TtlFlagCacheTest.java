package io.github.adeyinka7789.wunmi;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

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
    void invalidate_forcesReloadWithinTtl() {
        MutableClock clock = new MutableClock(0);
        TtlFlagCache cache = new TtlFlagCache(5000, clock);
        AtomicInteger loads = new AtomicInteger();

        cache.flags(() -> { loads.incrementAndGet(); return oneFlag(); });
        cache.invalidate();
        cache.flags(() -> { loads.incrementAndGet(); return oneFlag(); });   // TTL not expired

        assertThat(loads.get()).isEqualTo(2);
    }

    @Test
    void invalidate_alsoDropsMemoizedOverrides() {
        MutableClock clock = new MutableClock(0);
        TtlFlagCache cache = new TtlFlagCache(5000, clock);
        AtomicInteger loads = new AtomicInteger();
        Supplier<Optional<FlagOverride>> loader = () -> {
            loads.incrementAndGet();
            return Optional.of(FlagOverride.create("F", FlagOverride.Scope.SUBJECT, "u", true, null, "a"));
        };

        cache.override("k", loader);
        cache.invalidate();
        cache.override("k", loader);

        assertThat(loads.get()).isEqualTo(2);
    }

    /**
     * invalidate() nulls the snapshot reference, which a reader's compare-and-set can race with.
     * The reader must retry rather than hand back the (now null) current value.
     */
    @Test
    void concurrentInvalidate_neverYieldsNull() throws Exception {
        TtlFlagCache cache = new TtlFlagCache(0);   // always expired — every read takes the CAS path
        int readers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(readers + 1);
        AtomicBoolean stop = new AtomicBoolean();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < readers; i++) {
                futures.add(pool.submit(() -> {
                    while (!stop.get()) {
                        cache.flags(TtlFlagCacheTest::oneFlag);
                        cache.override("k", Optional::empty);
                    }
                }));
            }
            futures.add(pool.submit(() -> {
                while (!stop.get()) {
                    cache.invalidate();
                }
            }));
            Thread.sleep(300);
            stop.set(true);
            for (Future<?> f : futures) {
                f.get(5, TimeUnit.SECONDS);   // an NPE in any task surfaces here
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void noneCache_invalidateIsANoop() {
        FlagCache.NONE.invalidate();   // must not throw
    }

    @Test
    void noneCache_alwaysCallsLoader() {
        AtomicInteger loads = new AtomicInteger();
        FlagCache.NONE.flags(() -> { loads.incrementAndGet(); return oneFlag(); });
        FlagCache.NONE.flags(() -> { loads.incrementAndGet(); return oneFlag(); });
        assertThat(loads.get()).isEqualTo(2);
    }
}
