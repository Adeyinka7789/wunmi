package io.github.adeyinka7789.wunmi;

/**
 * Thrown by {@link FlagEngine#require(FlagKey)} when a required flag is not on for the current
 * context. Carries the flag key so callers can map it to a suitable response (e.g. HTTP 404).
 */
public class FlagDisabledException extends RuntimeException {

    private final String flagKey;

    public FlagDisabledException(String flagKey) {
        super("Feature flag '" + flagKey + "' is not enabled");
        this.flagKey = flagKey;
    }

    /** The key of the flag that was not enabled. */
    public String flagKey() {
        return flagKey;
    }
}
