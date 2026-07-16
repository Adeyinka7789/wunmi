package io.github.adeyinka7789.wunmi;

/**
 * A typed handle for a flag — the engine's public vocabulary. Reference a flag by an enum
 * constant that implements this interface rather than a bare string, so flag names are
 * discoverable, refactor-safe, and impossible to typo at the call site.
 *
 * <pre>{@code
 * public enum Feature implements FlagKey {
 *     DARK_MODE, BETA_CHECKOUT;
 *     public String key() { return name(); }
 * }
 *
 * if (flags.isOn(Feature.DARK_MODE)) { ... }
 * }</pre>
 *
 * The engine stores and resolves flags by their string {@link #key()}, so it stays agnostic of
 * any particular application's catalogue.
 */
public interface FlagKey {

    /** The stable string identifier this flag is stored under. */
    String key();
}
