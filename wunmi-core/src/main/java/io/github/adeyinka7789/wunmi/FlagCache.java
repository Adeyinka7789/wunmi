package io.github.adeyinka7789.wunmi;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Caching SPI. The engine looks up flags and overrides through this interface, so the caching
 * strategy is pluggable. Each method is a get-or-load: on a miss the loader runs and its result
 * is cached; on a hit the loader is not invoked.
 *
 * <p>Bundled implementations: {@link #NONE} (no caching) and {@link TtlFlagCache} (short-TTL,
 * good for jobs). A request-scoped cache ships with the Spring Boot starter.
 */
public interface FlagCache {

    /** All flags keyed by name — the engine resolves a single flag by {@code map.get(name)}. */
    Map<String, Flag> flags(Supplier<Map<String, Flag>> loader);

    /** A single override, memoized by {@code key} (a {@code flag|scope|value} tuple). */
    Optional<FlagOverride> override(String key, Supplier<Optional<FlagOverride>> loader);

    /** A cache that never caches — always calls the loader. */
    FlagCache NONE = new FlagCache() {
        @Override public Map<String, Flag> flags(Supplier<Map<String, Flag>> loader) {
            return loader.get();
        }
        @Override public Optional<FlagOverride> override(String key, Supplier<Optional<FlagOverride>> loader) {
            return loader.get();
        }
    };
}
