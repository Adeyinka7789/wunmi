package io.github.adeyinka7789.wunmi;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * A process-wide cache with a bounded time-to-live — the flag map is loaded at most once per TTL
 * window and overrides are memoized within it. Ideal outside a request scope (background jobs,
 * schedulers): a subject-sweeping job collapses to roughly one reload per window instead of a
 * query per flag per subject. A flag change propagates within the TTL.
 *
 * <p>Thread-safe: the whole snapshot is swapped atomically on expiry.
 */
public final class TtlFlagCache implements FlagCache {

    private final long ttlMillis;
    private final Clock clock;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();

    public TtlFlagCache(long ttlMillis) {
        this(ttlMillis, Clock.systemUTC());
    }

    /** Test seam: inject a controllable clock. */
    public TtlFlagCache(long ttlMillis, Clock clock) {
        if (ttlMillis < 0) {
            throw new IllegalArgumentException("ttlMillis must be >= 0");
        }
        this.ttlMillis = ttlMillis;
        this.clock = clock;
    }

    @Override
    public Map<String, Flag> flags(Supplier<Map<String, Flag>> loader) {
        return current().flags(loader);
    }

    @Override
    public Optional<FlagOverride> override(String key, Supplier<Optional<FlagOverride>> loader) {
        return current().overrides.computeIfAbsent(key, k -> loader.get());
    }

    private Snapshot current() {
        long now = clock.millis();
        Snapshot existing = snapshot.get();
        if (existing != null && now < existing.expiryMillis) {
            return existing;
        }
        Snapshot fresh = new Snapshot(now + ttlMillis);
        return snapshot.compareAndSet(existing, fresh) ? fresh : snapshot.get();
    }

    private static final class Snapshot {
        final long expiryMillis;
        final Map<String, Optional<FlagOverride>> overrides = new ConcurrentHashMap<>();
        private volatile Map<String, Flag> flags;

        Snapshot(long expiryMillis) {
            this.expiryMillis = expiryMillis;
        }

        Map<String, Flag> flags(Supplier<Map<String, Flag>> loader) {
            Map<String, Flag> local = flags;
            if (local == null) {
                synchronized (this) {
                    local = flags;
                    if (local == null) {
                        local = loader.get();
                        flags = local;
                    }
                }
            }
            return local;
        }
    }
}
