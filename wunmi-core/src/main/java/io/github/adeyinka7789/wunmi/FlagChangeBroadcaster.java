package io.github.adeyinka7789.wunmi;

/**
 * Propagates flag changes across application instances so a change made on one instance
 * invalidates the cached view on all the others (rather than waiting for their cache TTL).
 *
 * <p>The engine calls {@link #broadcastChange()} after every management write, and registers the
 * cache's invalidation via {@link #addListener} so an observed change (local or from a peer) clears
 * the local cache. Implementations range from {@link #NONE} (single instance — the TTL suffices) to
 * a database version-counter poller ({@code JdbcFlagChangeBroadcaster} in {@code wunmi-jdbc}, no
 * extra infrastructure) to a broker-backed fan-out (Redis/Kafka) you provide.
 */
public interface FlagChangeBroadcaster {

    /** Announce that flags changed on this instance, so peers invalidate their caches. */
    void broadcastChange();

    /** Register a listener fired when a change is observed — from this instance or a peer. */
    void addListener(Runnable listener);

    /** Single-instance default: no propagation (the cache TTL bounds staleness). */
    FlagChangeBroadcaster NONE = new FlagChangeBroadcaster() {
        @Override public void broadcastChange() { }
        @Override public void addListener(Runnable listener) { }
    };
}
