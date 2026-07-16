package io.github.adeyinka7789.wunmi;

import io.github.adeyinka7789.wunmi.FlagAuditListener.FlagChange;
import io.github.adeyinka7789.wunmi.FlagOverride.Scope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlagEngineTest {

    private enum Feature implements FlagKey {
        DARK_MODE, BETA;
        public String key() { return name(); }
    }

    private final InMemoryFlagStore store = new InMemoryFlagStore();
    private final List<FlagChange> audit = new ArrayList<>();
    private FlagContext context = FlagContext.EMPTY;

    private FlagEngine engine() {
        return new FlagEngine(store, FlagCache.NONE, audit::add, () -> context);
    }

    // ── Resolution ─────────────────────────────────────────────────────────────

    @Test
    void unregisteredFlag_failsClosed() {
        assertThat(engine().isEnabled("MISSING")).isFalse();
    }

    @Test
    void enabledFlag_defaultsOn() {
        store.seed(Flag.enabledFlag("DARK_MODE"));
        assertThat(engine().isEnabled(Feature.DARK_MODE)).isTrue();
    }

    @Test
    void globalKillSwitch_off() {
        store.seed(new Flag("DARK_MODE", false, null, 100, null));
        assertThat(engine().resolve("DARK_MODE", "user-1", "pro")).isFalse();
        // No override is consulted once the global switch is off.
        assertThat(store.findOverrideCalls).isZero();
    }

    @Test
    void subjectOverride_wins() {
        store.seed(Flag.enabledFlag("DARK_MODE"));
        store.saveOverride(FlagOverride.create("DARK_MODE", Scope.SUBJECT, "user-1", false, "opt-out", "admin"));
        assertThat(engine().resolve("DARK_MODE", "user-1", null)).isFalse();
        assertThat(engine().resolve("DARK_MODE", "user-2", null)).isTrue();
    }

    @Test
    void segmentOverride_appliesWhenNoSubjectOverride() {
        store.seed(Flag.enabledFlag("DARK_MODE"));
        store.saveOverride(FlagOverride.create("DARK_MODE", Scope.SEGMENT, "free", false, null, "admin"));
        assertThat(engine().resolve("DARK_MODE", "user-1", "free")).isFalse();
        assertThat(engine().resolve("DARK_MODE", "user-1", "pro")).isTrue();
    }

    @Test
    void subjectOverride_beatsSegmentOverride() {
        store.seed(Flag.enabledFlag("DARK_MODE"));
        store.saveOverride(FlagOverride.create("DARK_MODE", Scope.SUBJECT, "user-1", true, null, "admin"));
        store.saveOverride(FlagOverride.create("DARK_MODE", Scope.SEGMENT, "free", false, null, "admin"));
        assertThat(engine().resolve("DARK_MODE", "user-1", "free")).isTrue();
    }

    @Test
    void rolloutPercentage_bucketsConsistently() {
        store.seed(new Flag("BETA", true, null, 50, null));
        String subject = "user-42";
        int bucket = Math.floorMod((subject + "BETA").hashCode(), 100);
        assertThat(engine().resolve("BETA", subject, null)).isEqualTo(bucket < 50);
    }

    @Test
    void rolloutZero_offForEveryone_butGlobalStillEnabled() {
        store.seed(new Flag("BETA", true, null, 0, null));
        assertThat(engine().resolve("BETA", "anyone", null)).isFalse();
        assertThat(engine().isEnabled("BETA")).isTrue(); // global-only check ignores rollout
    }

    @Test
    void isOn_usesContextResolver() {
        store.seed(Flag.enabledFlag("DARK_MODE"));
        store.saveOverride(FlagOverride.create("DARK_MODE", Scope.SUBJECT, "user-1", false, null, "admin"));
        context = FlagContext.ofSubject("user-1");
        assertThat(engine().isOn(Feature.DARK_MODE)).isFalse();
    }

    @Test
    void require_throwsWhenOff() {
        store.seed(new Flag("DARK_MODE", false, null, 100, null));
        assertThatThrownBy(() -> engine().require(Feature.DARK_MODE))
                .isInstanceOf(FlagDisabledException.class)
                .satisfies(ex -> assertThat(((FlagDisabledException) ex).flagKey()).isEqualTo("DARK_MODE"));
    }

    // ── Management ─────────────────────────────────────────────────────────────

    @Test
    void disable_thenEnable_updatesAndAudits() {
        store.seed(Flag.enabledFlag("DARK_MODE"));
        FlagEngine engine = engine();

        assertThat(engine.disable("DARK_MODE", "admin").enabled()).isFalse();
        assertThat(engine.isEnabled("DARK_MODE")).isFalse();
        assertThat(engine.enable("DARK_MODE", "admin").enabled()).isTrue();

        assertThat(audit).extracting(FlagChange::detail).contains("enabled=false", "enabled=true");
    }

    @Test
    void setRollout_outOfRange_rejected() {
        assertThatThrownBy(() -> engine().setRollout("BETA", 150, "admin"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void putOverride_onUnregisteredFlag_autoCreatesBackingFlag() {
        FlagEngine engine = engine();
        engine.putOverride("NEW", Scope.SUBJECT, "user-1", true, "beta", "admin");

        // The flag now exists (enabled) so the override actually takes effect.
        assertThat(store.findFlag("NEW")).isPresent();
        assertThat(engine.resolve("NEW", "user-1", null)).isTrue();
    }

    @Test
    void removeOverride_deletesAndAudits() {
        store.seed(Flag.enabledFlag("DARK_MODE"));
        FlagOverride saved = engine().putOverride("DARK_MODE", Scope.SUBJECT, "user-1", false, null, "admin");
        audit.clear();

        engine().removeOverride(saved.id(), "admin");

        assertThat(store.findOverrides("DARK_MODE")).isEmpty();
        assertThat(audit).extracting(FlagChange::detail).anyMatch(d -> d.startsWith("override removed"));
    }
}
