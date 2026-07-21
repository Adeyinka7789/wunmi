package io.github.adeyinka7789.wunmi;

import io.github.adeyinka7789.wunmi.FlagEvaluationListener.FlagEvaluation;
import io.github.adeyinka7789.wunmi.FlagEvaluationListener.Reason;
import io.github.adeyinka7789.wunmi.FlagOverride.Scope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlagEvaluationListenerTest {

    private final InMemoryFlagStore store = new InMemoryFlagStore();
    private final List<FlagEvaluation> evaluations = new ArrayList<>();

    private FlagEngine engine() {
        return new FlagEngine(store, FlagCache.NONE, FlagAuditListener.NOOP,
                FlagContextResolver.EMPTY, FlagChangeBroadcaster.NONE, evaluations::add);
    }

    private Reason lastReason() {
        return evaluations.get(evaluations.size() - 1).reason();
    }

    @Test
    void notFound_emitsNotFound() {
        engine().resolve("MISSING", "user-1", null);
        assertThat(lastReason()).isEqualTo(Reason.NOT_FOUND);
    }

    @Test
    void globalOff_emitsGlobalDisabled() {
        store.seed(new Flag("DARK_MODE", false, null, 100, null));
        engine().resolve("DARK_MODE", "user-1", null);
        assertThat(lastReason()).isEqualTo(Reason.GLOBAL_DISABLED);
    }

    @Test
    void subjectOverride_emitsSubjectOverride() {
        store.seed(Flag.enabledFlag("DARK_MODE"));
        store.saveOverride(FlagOverride.create("DARK_MODE", Scope.SUBJECT, "user-1", false, null, "admin"));
        engine().resolve("DARK_MODE", "user-1", null);
        assertThat(lastReason()).isEqualTo(Reason.SUBJECT_OVERRIDE);
    }

    @Test
    void segmentOverride_emitsSegmentOverride() {
        store.seed(Flag.enabledFlag("DARK_MODE"));
        store.saveOverride(FlagOverride.create("DARK_MODE", Scope.SEGMENT, "free", false, null, "admin"));
        engine().resolve("DARK_MODE", "user-1", "free");
        assertThat(lastReason()).isEqualTo(Reason.SEGMENT_OVERRIDE);
    }

    @Test
    void rollout_emitsIncludedOrExcluded() {
        store.seed(new Flag("BETA", true, null, 50, null));
        String subject = "user-42";
        boolean included = Math.floorMod((subject + "BETA").hashCode(), 100) < 50;
        engine().resolve("BETA", subject, null);
        assertThat(lastReason()).isEqualTo(included ? Reason.ROLLOUT_INCLUDED : Reason.ROLLOUT_EXCLUDED);
    }

    @Test
    void defaultOn_emitsDefaultOn() {
        store.seed(Flag.enabledFlag("DARK_MODE"));
        engine().resolve("DARK_MODE", "user-1", null);
        assertThat(lastReason()).isEqualTo(Reason.DEFAULT_ON);
    }

    @Test
    void evaluationCarriesFlagNameAndResult() {
        store.seed(Flag.enabledFlag("DARK_MODE"));
        engine().resolve("DARK_MODE", null, null);
        FlagEvaluation last = evaluations.get(evaluations.size() - 1);
        assertThat(last.flagName()).isEqualTo("DARK_MODE");
        assertThat(last.enabled()).isTrue();
    }
}
