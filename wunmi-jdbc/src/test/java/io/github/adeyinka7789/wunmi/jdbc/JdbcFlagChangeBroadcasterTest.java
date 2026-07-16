package io.github.adeyinka7789.wunmi.jdbc;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcFlagChangeBroadcasterTest {

    private static final long POLL_MS = 20;
    private static final long AWAIT_SECONDS = 5;

    private DataSource dataSource;
    private final List<JdbcFlagChangeBroadcaster> started = new ArrayList<>();

    @BeforeEach
    void setUp() {
        dataSource = h2("wunmi_bcast_" + UUID.randomUUID());
        WunmiSchema.initialize(dataSource);
    }

    @AfterEach
    void tearDown() {
        started.forEach(JdbcFlagChangeBroadcaster::close);
    }

    @Test
    void peerSeesChange_whenAnotherInstanceBroadcasts() throws Exception {
        JdbcFlagChangeBroadcaster peer = start(dataSource);
        CountDownLatch observed = new CountDownLatch(1);
        peer.addListener(observed::countDown);

        // A second instance on the same database flips a flag.
        JdbcFlagChangeBroadcaster other = new JdbcFlagChangeBroadcaster(dataSource, POLL_MS);
        other.broadcastChange();

        assertThat(observed.await(AWAIT_SECONDS, TimeUnit.SECONDS))
                .as("peer should observe the change by polling the version counter")
                .isTrue();
    }

    @Test
    void doesNotFire_whenNothingChanged() throws Exception {
        JdbcFlagChangeBroadcaster peer = start(dataSource);
        AtomicInteger fired = new AtomicInteger();
        peer.addListener(fired::incrementAndGet);

        Thread.sleep(POLL_MS * 10);

        assertThat(fired).hasValue(0);
    }

    @Test
    void doesNotFireForPreExistingState_whenStartingAfterAChange() throws Exception {
        new JdbcFlagChangeBroadcaster(dataSource, POLL_MS).broadcastChange();   // before we start

        JdbcFlagChangeBroadcaster late = start(dataSource);
        AtomicInteger fired = new AtomicInteger();
        late.addListener(fired::incrementAndGet);

        Thread.sleep(POLL_MS * 10);

        assertThat(fired).as("start() baselines the version, so history must not fire").hasValue(0);
    }

    @Test
    void seedsCounterRow_whenBroadcastingBeforeAnyStart() throws Exception {
        // A fresh schema has no counter row, so the UPDATE matches nothing and must fall back to
        // seeding — otherwise the very first change of a deployment's life would go unnoticed.
        new JdbcFlagChangeBroadcaster(dataSource, POLL_MS).broadcastChange();

        assertThat(versionInDb()).isEqualTo(1);
    }

    @Test
    void observesChanges_whenStartingOnAnAlreadySeededCounter() throws Exception {
        new JdbcFlagChangeBroadcaster(dataSource, POLL_MS).broadcastChange();   // seeds the row

        JdbcFlagChangeBroadcaster peer = start(dataSource);
        assertThatCode(peer::start).doesNotThrowAnyException();   // idempotent
        CountDownLatch observed = new CountDownLatch(1);
        peer.addListener(observed::countDown);

        new JdbcFlagChangeBroadcaster(dataSource, POLL_MS).broadcastChange();

        assertThat(observed.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    private long versionInDb() throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT version FROM wunmi_flag_version WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : -1L;
        }
    }

    @Test
    void degradesGracefully_whenVersionTableMissing() throws Exception {
        DataSource bare = h2("wunmi_noschema_" + UUID.randomUUID());   // no WunmiSchema.initialize
        JdbcFlagChangeBroadcaster broadcaster = new JdbcFlagChangeBroadcaster(bare, POLL_MS);
        started.add(broadcaster);
        AtomicInteger fired = new AtomicInteger();
        broadcaster.addListener(fired::incrementAndGet);

        assertThatCode(broadcaster::start)
                .as("a missing counter table must not fail app startup")
                .doesNotThrowAnyException();
        assertThatCode(broadcaster::broadcastChange)
                .as("broadcast failures are non-fatal — peers converge via TTL")
                .doesNotThrowAnyException();

        Thread.sleep(POLL_MS * 10);
        assertThat(fired).as("polling should be off, not looping on errors").hasValue(0);
    }

    @Test
    void rejectsNonPositivePollInterval() {
        assertThatThrownBy(() -> new JdbcFlagChangeBroadcaster(dataSource, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stopsPolling_afterClose() throws Exception {
        JdbcFlagChangeBroadcaster peer = start(dataSource);
        AtomicInteger fired = new AtomicInteger();
        peer.addListener(fired::incrementAndGet);
        peer.close();

        new JdbcFlagChangeBroadcaster(dataSource, POLL_MS).broadcastChange();
        Thread.sleep(POLL_MS * 10);

        assertThat(fired).hasValue(0);
    }

    private JdbcFlagChangeBroadcaster start(DataSource ds) {
        JdbcFlagChangeBroadcaster b = new JdbcFlagChangeBroadcaster(ds, POLL_MS);
        started.add(b);
        b.start();
        return b;
    }

    private static DataSource h2(String name) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        return ds;
    }
}
