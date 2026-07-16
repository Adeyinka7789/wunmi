package io.github.adeyinka7789.wunmi.jdbc;

import io.github.adeyinka7789.wunmi.Flag;
import io.github.adeyinka7789.wunmi.FlagEngine;
import io.github.adeyinka7789.wunmi.FlagOverride;
import io.github.adeyinka7789.wunmi.FlagOverride.Scope;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcFlagStoreTest {

    private JdbcFlagStore store;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:wunmi_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        DataSource dataSource = ds;
        WunmiSchema.initialize(dataSource);
        WunmiSchema.initialize(dataSource); // idempotent — second call must not fail
        store = new JdbcFlagStore(dataSource);
    }

    @Test
    void saveFlag_insertsThenUpdates() {
        store.saveFlag(new Flag("DARK_MODE", true, "beta", 100, "admin"));
        assertThat(store.findFlag("DARK_MODE")).get()
                .isEqualTo(new Flag("DARK_MODE", true, "beta", 100, "admin"));

        store.saveFlag(new Flag("DARK_MODE", false, "beta", 25, "ops"));
        Flag updated = store.findFlag("DARK_MODE").orElseThrow();
        assertThat(updated.enabled()).isFalse();
        assertThat(updated.rolloutPercentage()).isEqualTo(25);
        assertThat(updated.updatedBy()).isEqualTo("ops");
    }

    @Test
    void findAllFlags_returnsEverything() {
        store.saveFlag(Flag.enabledFlag("A"));
        store.saveFlag(Flag.enabledFlag("B"));
        assertThat(store.findAllFlags()).extracting(Flag::name).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void unknownFlag_isEmpty() {
        assertThat(store.findFlag("NOPE")).isEmpty();
    }

    @Test
    void saveOverride_assignsIdAndRoundTrips() {
        FlagOverride saved = store.saveOverride(
                FlagOverride.create("DARK_MODE", Scope.SUBJECT, "user-1", false, "opt-out", "admin"));

        assertThat(saved.id()).isNotNull();
        assertThat(store.findOverride("DARK_MODE", Scope.SUBJECT, "user-1")).get().isEqualTo(saved);
        assertThat(store.findOverrideById(saved.id())).get().isEqualTo(saved);
        assertThat(store.findOverrides("DARK_MODE")).containsExactly(saved);
    }

    @Test
    void saveOverride_updatesExistingById() {
        FlagOverride saved = store.saveOverride(
                FlagOverride.create("DARK_MODE", Scope.SEGMENT, "free", false, null, "admin"));
        store.saveOverride(saved.withState(true, "changed mind", "ops"));

        FlagOverride current = store.findOverride("DARK_MODE", Scope.SEGMENT, "free").orElseThrow();
        assertThat(current.id()).isEqualTo(saved.id());
        assertThat(current.enabled()).isTrue();
        assertThat(current.reason()).isEqualTo("changed mind");
    }

    @Test
    void deleteOverride_removesIt() {
        FlagOverride saved = store.saveOverride(
                FlagOverride.create("DARK_MODE", Scope.SUBJECT, "user-1", true, null, "admin"));
        store.deleteOverride(saved.id());
        assertThat(store.findOverrideById(saved.id())).isEmpty();
    }

    @Test
    void worksEndToEndThroughEngine() {
        FlagEngine engine = new FlagEngine(store);
        engine.enable("DARK_MODE", "admin");
        engine.putOverride("DARK_MODE", Scope.SUBJECT, "user-1", false, "opt-out", "admin");

        assertThat(engine.resolve("DARK_MODE", "user-2", null)).isTrue();
        assertThat(engine.resolve("DARK_MODE", "user-1", null)).isFalse();
    }
}
