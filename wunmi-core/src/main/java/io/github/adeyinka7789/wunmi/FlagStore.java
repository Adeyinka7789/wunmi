package io.github.adeyinka7789.wunmi;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence SPI — every read and write the engine performs against flag and override storage
 * goes through here. Implement it over whatever you like (SQL, a document store, an in-memory
 * map for tests); the engine has no other persistence dependency.
 *
 * <p>Implementations are responsible for their own transactions/atomicity. All methods may be
 * called from any thread.
 */
public interface FlagStore {

    // ── Flags ──────────────────────────────────────────────────────────────────
    Optional<Flag> findFlag(String name);

    List<Flag> findAllFlags();

    /** Insert or update by {@link Flag#name()}, returning the persisted flag. */
    Flag saveFlag(Flag flag);

    // ── Overrides ──────────────────────────────────────────────────────────────
    Optional<FlagOverride> findOverride(String flagName, FlagOverride.Scope scope, String value);

    List<FlagOverride> findOverrides(String flagName);

    /** Insert (id {@code null}) or update by id, returning the persisted override with its id. */
    FlagOverride saveOverride(FlagOverride override);

    Optional<FlagOverride> findOverrideById(UUID id);

    void deleteOverride(UUID id);
}
