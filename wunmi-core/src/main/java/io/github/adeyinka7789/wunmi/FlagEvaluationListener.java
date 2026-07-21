package io.github.adeyinka7789.wunmi;

/**
 * Notified on every flag resolution, so you can record metrics (evaluation counts by flag and
 * outcome, hit rates, …). This is the hot-path counterpart to {@link FlagAuditListener}, which
 * fires only on configuration <i>changes</i>. Supply your own — a thin Micrometer adapter is the
 * common case — or use {@link #NOOP}.
 *
 * <p>The listener runs inline on the resolving thread, so keep it cheap and non-blocking. It must
 * not throw; the engine does not guard against it, and a thrown exception would fail the caller's
 * flag check.
 *
 * <p><b>Cardinality note:</b> the subject id is deliberately not exposed here. {@code flagName} and
 * {@link Reason} are low-cardinality and safe as metric tags; subject ids are not.
 */
@FunctionalInterface
public interface FlagEvaluationListener {

    /** A no-op listener. */
    FlagEvaluationListener NOOP = evaluation -> { };

    void onFlagEvaluated(FlagEvaluation evaluation);

    /**
     * The outcome of a single resolution.
     *
     * @param flagName the flag resolved
     * @param enabled  the result returned to the caller
     * @param reason   which resolution layer decided the result
     */
    record FlagEvaluation(String flagName, boolean enabled, Reason reason) { }

    /**
     * Which layer of the resolution order (see {@link FlagEngine}) produced the result — the
     * dimension you'll most often tag metrics by.
     */
    enum Reason {
        /** No stored flag; failed closed. */
        NOT_FOUND,
        /** The global kill switch was off. */
        GLOBAL_DISABLED,
        /** A matching subject override decided it. */
        SUBJECT_OVERRIDE,
        /** A matching segment override decided it. */
        SEGMENT_OVERRIDE,
        /** The subject fell inside the rollout bucket. */
        ROLLOUT_INCLUDED,
        /** The subject fell outside the rollout bucket. */
        ROLLOUT_EXCLUDED,
        /** No override or rollout applied; on by default. */
        DEFAULT_ON
    }
}
