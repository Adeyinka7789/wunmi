package io.github.adeyinka7789.wunmi;

/**
 * Notified whenever a flag, override, or rollout changes, so you can record an audit trail.
 * Supply your own, or use {@link #NOOP}.
 */
@FunctionalInterface
public interface FlagAuditListener {

    /** A no-op listener. */
    FlagAuditListener NOOP = change -> { };

    void onFlagChanged(FlagChange change);

    /**
     * A single flag-configuration change.
     *
     * @param flagName the flag affected
     * @param actor    who made the change (may be {@code null})
     * @param detail   human-readable description of what changed
     */
    record FlagChange(String flagName, String actor, String detail) { }
}
