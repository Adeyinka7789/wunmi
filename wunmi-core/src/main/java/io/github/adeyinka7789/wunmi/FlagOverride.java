package io.github.adeyinka7789.wunmi;

import java.util.UUID;

/**
 * A targeted override that forces a flag on or off for one subject or one segment, taking
 * precedence over the flag's rollout (but never over a global kill switch — a disabled flag
 * stays off). Immutable.
 *
 * @param id        store-assigned identity; {@code null} for an override not yet persisted
 * @param flagName  the flag this override applies to
 * @param scope     whether {@code value} names a subject or a segment
 * @param value     the subject id ({@code scope == SUBJECT}) or segment name ({@code scope == SEGMENT})
 * @param enabled   the forced value for the matched subject/segment
 * @param reason    optional human-readable justification
 * @param createdBy who created/last changed this override (may be {@code null})
 */
public record FlagOverride(UUID id, String flagName, Scope scope, String value,
                           boolean enabled, String reason, String createdBy) {

    /** The dimension an override targets. Mirrors the two nullable fields of {@link FlagContext}. */
    public enum Scope {
        /** Matches {@link FlagContext#subjectId()}. */
        SUBJECT,
        /** Matches {@link FlagContext#segment()}. */
        SEGMENT
    }

    /** A new, unpersisted override (no id). */
    public static FlagOverride create(String flagName, Scope scope, String value,
                                      boolean enabled, String reason, String createdBy) {
        return new FlagOverride(null, flagName, scope, value, enabled, reason, createdBy);
    }

    /** A copy with an updated enabled/reason/actor, preserving id and target. */
    public FlagOverride withState(boolean enabled, String reason, String createdBy) {
        return new FlagOverride(id, flagName, scope, value, enabled, reason, createdBy);
    }
}
