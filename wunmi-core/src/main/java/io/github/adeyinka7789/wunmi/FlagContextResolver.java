package io.github.adeyinka7789.wunmi;

/**
 * Resolves the current {@link FlagContext} so the engine can offer a no-argument
 * {@link FlagEngine#isOn(FlagKey)} without knowing where the caller's identity lives (a
 * thread-local, a security context, a request header, …). Supply your own implementation.
 */
@FunctionalInterface
public interface FlagContextResolver {

    /** The current context, or {@link FlagContext#EMPTY} when nothing is bound. */
    FlagContext currentContext();

    /** A resolver that always returns {@link FlagContext#EMPTY} (global resolution only). */
    FlagContextResolver EMPTY = () -> FlagContext.EMPTY;
}
