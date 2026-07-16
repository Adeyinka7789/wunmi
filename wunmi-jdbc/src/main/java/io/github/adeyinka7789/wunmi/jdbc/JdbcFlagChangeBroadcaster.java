package io.github.adeyinka7789.wunmi.jdbc;

import io.github.adeyinka7789.wunmi.FlagChangeBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A zero-infrastructure {@link FlagChangeBroadcaster}: cross-instance cache invalidation using a
 * single-row generation counter ({@code wunmi_flag_version}) in the database. A management write
 * bumps the counter; every instance polls it cheaply on a fixed interval and, when it changes,
 * fires its listeners (which clear the local cache). No message broker required — just the DB you
 * already have. Staleness is bounded by the poll interval.
 *
 * <p>{@link #start()} begins polling; {@link #close()} stops it. In Spring Boot the starter manages
 * this lifecycle for you.
 */
public class JdbcFlagChangeBroadcaster implements FlagChangeBroadcaster, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(JdbcFlagChangeBroadcaster.class);
    private static final int ROW_ID = 1;

    private final DataSource dataSource;
    private final long pollIntervalMs;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong lastSeenVersion = new AtomicLong(0);
    private volatile ScheduledExecutorService poller;

    public JdbcFlagChangeBroadcaster(DataSource dataSource, long pollIntervalMs) {
        if (pollIntervalMs <= 0) {
            throw new IllegalArgumentException("pollIntervalMs must be > 0");
        }
        this.dataSource = dataSource;
        this.pollIntervalMs = pollIntervalMs;
    }

    /**
     * Ensure the counter row exists, baseline the version, and start the poll loop. Idempotent.
     *
     * <p>If the counter table can't be reached — typically an app that manages its own schema and
     * hasn't added {@code wunmi_flag_version} yet — this warns and leaves polling off rather than
     * failing startup: caches then converge within their TTL, exactly as they did before
     * cross-instance invalidation existed.
     */
    public synchronized void start() {
        if (poller != null) {
            return;
        }
        try {
            ensureRow();
            lastSeenVersion.set(currentVersion());   // baseline — don't fire for pre-existing state
        } catch (RuntimeException e) {
            log.warn("wunmi: cross-instance flag invalidation is off — could not access "
                    + "wunmi_flag_version ({}). Create it (see wunmi-jdbc schema.sql, or set "
                    + "wunmi.jdbc.initialize-schema=true), or set wunmi.invalidation.enabled=false "
                    + "to silence this. Flag changes still reach other instances within the cache TTL.",
                    e.getMessage());
            return;
        }
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "wunmi-flag-poller");
            t.setDaemon(true);
            return t;
        });
        exec.scheduleWithFixedDelay(this::poll, pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
        this.poller = exec;
    }

    @Override
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    @Override
    public void broadcastChange() {
        try (Connection c = dataSource.getConnection()) {
            int updated;
            try (PreparedStatement up = c.prepareStatement(
                    "UPDATE wunmi_flag_version SET version = version + 1 WHERE id = ?")) {
                up.setInt(1, ROW_ID);
                updated = up.executeUpdate();
            }
            if (updated == 0) {
                insertRow(c, 1);
            }
        } catch (SQLException e) {
            // Non-fatal: peers will still converge via TTL; local cache was already invalidated.
            log.warn("wunmi: failed to broadcast flag change: {}", e.getMessage());
        }
    }

    private void poll() {
        try {
            long current = currentVersion();
            long previous = lastSeenVersion.get();
            if (current != previous && lastSeenVersion.compareAndSet(previous, current)) {
                listeners.forEach(Runnable::run);
            }
        } catch (RuntimeException e) {
            log.warn("wunmi: flag version poll failed: {}", e.getMessage());   // keep the poller alive
        }
    }

    private long currentVersion() {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT version FROM wunmi_flag_version WHERE id = ?")) {
            ps.setInt(1, ROW_ID);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new FlagStoreException("failed to read wunmi_flag_version", e);
        }
    }

    private void ensureRow() {
        // One connection, one query: nesting currentVersion()'s own getConnection() inside this
        // try-with-resources would hold two at once and deadlock a single-connection pool.
        try (Connection c = dataSource.getConnection()) {
            if (!rowExists(c)) {
                insertRow(c, 0);
            }
        } catch (SQLException e) {
            throw new FlagStoreException("failed to initialize wunmi_flag_version", e);
        }
    }

    private boolean rowExists(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM wunmi_flag_version WHERE id = ?")) {
            ps.setInt(1, ROW_ID);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void insertRow(Connection c, long version) {
        try (PreparedStatement ins = c.prepareStatement(
                "INSERT INTO wunmi_flag_version (id, version) VALUES (?, ?)")) {
            ins.setInt(1, ROW_ID);
            ins.setLong(2, version);
            ins.executeUpdate();
        } catch (SQLException e) {
            // Another instance seeded it concurrently — fine.
            log.debug("wunmi: version row already seeded ({})", e.getMessage());
        }
    }

    @Override
    public synchronized void close() {
        if (poller != null) {
            poller.shutdownNow();
            poller = null;
        }
    }
}
