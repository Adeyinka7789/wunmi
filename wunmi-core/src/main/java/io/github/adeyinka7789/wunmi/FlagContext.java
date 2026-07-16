package io.github.adeyinka7789.wunmi;

/**
 * The targeting context a flag is evaluated against — <i>who</i> is asking.
 *
 * @param subjectId the primary subject (a user id, tenant id, device id, …). Drives
 *                  {@link FlagOverride.Scope#SUBJECT} overrides and rollout bucketing.
 *                  {@code null} when unbound — both layers are then skipped.
 * @param segment   a named group the subject belongs to (a plan, cohort, region, …). Drives
 *                  {@link FlagOverride.Scope#SEGMENT} overrides. {@code null} when unknown.
 */
public record FlagContext(String subjectId, String segment) {

    /** No subject and no segment — global resolution only (kill switch / default). */
    public static final FlagContext EMPTY = new FlagContext(null, null);

    /** Context with a subject and no segment. */
    public static FlagContext ofSubject(String subjectId) {
        return new FlagContext(subjectId, null);
    }
}
