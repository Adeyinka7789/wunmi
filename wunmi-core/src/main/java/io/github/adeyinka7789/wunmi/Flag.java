package io.github.adeyinka7789.wunmi;

/**
 * A feature flag: a global on/off switch with an optional percentage rollout. Immutable — the
 * {@code with*} helpers return a modified copy, which is what the engine's management methods
 * persist through {@link FlagStore#saveFlag}.
 *
 * @param name              the flag's stable identifier (matches {@link FlagKey#key()})
 * @param enabled           the global kill switch — when {@code false}, no override or rollout
 *                          can turn the flag on
 * @param description       optional human-readable note
 * @param rolloutPercentage 0–100; the share of subjects (by consistent hash) that get the flag
 *                          when no override applies. 100 = everyone.
 * @param updatedBy         who last changed the flag (may be {@code null})
 */
public record Flag(String name, boolean enabled, String description, int rolloutPercentage, String updatedBy) {

    public Flag {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Flag name must not be blank");
        }
        if (rolloutPercentage < 0 || rolloutPercentage > 100) {
            throw new IllegalArgumentException("Rollout percentage must be between 0 and 100");
        }
    }

    /** A new flag, enabled, full rollout, no description. */
    public static Flag enabledFlag(String name) {
        return new Flag(name, true, null, 100, null);
    }

    public Flag withEnabled(boolean enabled, String updatedBy) {
        return new Flag(name, enabled, description, rolloutPercentage, updatedBy);
    }

    public Flag withRolloutPercentage(int rolloutPercentage, String updatedBy) {
        return new Flag(name, enabled, description, rolloutPercentage, updatedBy);
    }

    public Flag withDescription(String description) {
        return new Flag(name, enabled, description, rolloutPercentage, updatedBy);
    }
}
