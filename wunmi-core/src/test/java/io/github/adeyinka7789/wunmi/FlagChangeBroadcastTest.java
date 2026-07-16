package io.github.adeyinka7789.wunmi;

import io.github.adeyinka7789.wunmi.FlagOverride.Scope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The engine's side of cross-instance invalidation: every management write must clear the local
 * cache and tell peers, and an observed change (local or peer) must clear the local cache.
 */
class FlagChangeBroadcastTest {

    /** Records invalidations and serves a fixed view, so we can see staleness directly. */
    private static final class CountingCache implements FlagCache {
        int invalidations;

        @Override public Map<String, Flag> flags(Supplier<Map<String, Flag>> loader) {
            return loader.get();
        }

        @Override public Optional<FlagOverride> override(String key, Supplier<Optional<FlagOverride>> loader) {
            return loader.get();
        }

        @Override public void invalidate() {
            invalidations++;
        }
    }

    private static final class RecordingBroadcaster implements FlagChangeBroadcaster {
        int broadcasts;
        final List<Runnable> listeners = new ArrayList<>();

        @Override public void broadcastChange() {
            broadcasts++;
        }

        @Override public void addListener(Runnable listener) {
            listeners.add(listener);
        }

        /** Simulate a change made on another instance. */
        void firePeerChange() {
            listeners.forEach(Runnable::run);
        }
    }

    private final InMemoryFlagStore store = new InMemoryFlagStore();
    private final CountingCache cache = new CountingCache();
    private final RecordingBroadcaster broadcaster = new RecordingBroadcaster();

    private FlagEngine engine() {
        return new FlagEngine(store, cache, FlagAuditListener.NOOP, FlagContextResolver.EMPTY, broadcaster);
    }

    @Test
    void enable_invalidatesLocallyAndBroadcasts() {
        engine().enable("DARK_MODE", "admin");

        assertThat(cache.invalidations).isPositive();
        assertThat(broadcaster.broadcasts).isEqualTo(1);
    }

    @Test
    void disable_invalidatesLocallyAndBroadcasts() {
        engine().disable("DARK_MODE", "admin");

        assertThat(cache.invalidations).isPositive();
        assertThat(broadcaster.broadcasts).isEqualTo(1);
    }

    @Test
    void setRollout_invalidatesLocallyAndBroadcasts() {
        store.seed(Flag.enabledFlag("DARK_MODE"));
        engine().setRollout("DARK_MODE", 25, "admin");

        assertThat(cache.invalidations).isPositive();
        assertThat(broadcaster.broadcasts).isEqualTo(1);
    }

    @Test
    void putOverride_invalidatesLocallyAndBroadcasts() {
        engine().putOverride("DARK_MODE", Scope.SUBJECT, "tenant-1", true, "pilot", "admin");

        assertThat(cache.invalidations).isPositive();
        assertThat(broadcaster.broadcasts).isEqualTo(1);
    }

    @Test
    void removeOverride_invalidatesLocallyAndBroadcasts() {
        FlagEngine engine = engine();
        FlagOverride saved = engine.putOverride("DARK_MODE", Scope.SUBJECT, "tenant-1", true, "pilot", "admin");
        int before = broadcaster.broadcasts;

        engine.removeOverride(saved.id(), "admin");

        assertThat(broadcaster.broadcasts).isEqualTo(before + 1);
    }

    @Test
    void peerChange_invalidatesLocalCache() {
        engine();   // registers the cache's invalidation with the broadcaster
        int before = cache.invalidations;

        broadcaster.firePeerChange();

        assertThat(cache.invalidations).isEqualTo(before + 1);
    }

    @Test
    void defaultsToNoBroadcaster_whenConstructedWithoutOne() {
        FlagEngine engine = new FlagEngine(store, cache, FlagAuditListener.NOOP, FlagContextResolver.EMPTY);

        engine.enable("DARK_MODE", "admin");   // must not throw

        assertThat(cache.invalidations).as("local cache is still cleared").isPositive();
        assertThat(engine.isEnabled("DARK_MODE")).isTrue();
    }
}
