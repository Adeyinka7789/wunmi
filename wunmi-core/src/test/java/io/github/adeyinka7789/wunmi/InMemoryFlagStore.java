package io.github.adeyinka7789.wunmi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A simple in-memory {@link FlagStore} for tests, with call counters so cache behaviour can be
 * asserted. Not thread-safe — fine for single-threaded unit tests.
 */
class InMemoryFlagStore implements FlagStore {

    private final Map<String, Flag> flags = new HashMap<>();
    private final Map<UUID, FlagOverride> overrides = new HashMap<>();

    int findAllFlagsCalls = 0;
    int findOverrideCalls = 0;

    @Override
    public Optional<Flag> findFlag(String name) {
        return Optional.ofNullable(flags.get(name));
    }

    @Override
    public List<Flag> findAllFlags() {
        findAllFlagsCalls++;
        return new ArrayList<>(flags.values());
    }

    @Override
    public Flag saveFlag(Flag flag) {
        flags.put(flag.name(), flag);
        return flag;
    }

    @Override
    public Optional<FlagOverride> findOverride(String flagName, FlagOverride.Scope scope, String value) {
        findOverrideCalls++;
        return overrides.values().stream()
                .filter(o -> o.flagName().equals(flagName) && o.scope() == scope && o.value().equals(value))
                .findFirst();
    }

    @Override
    public List<FlagOverride> findOverrides(String flagName) {
        return overrides.values().stream().filter(o -> o.flagName().equals(flagName)).toList();
    }

    @Override
    public FlagOverride saveOverride(FlagOverride override) {
        UUID id = override.id() != null ? override.id() : UUID.randomUUID();
        FlagOverride persisted = new FlagOverride(id, override.flagName(), override.scope(),
                override.value(), override.enabled(), override.reason(), override.createdBy());
        overrides.put(id, persisted);
        return persisted;
    }

    @Override
    public Optional<FlagOverride> findOverrideById(UUID id) {
        return Optional.ofNullable(overrides.get(id));
    }

    @Override
    public void deleteOverride(UUID id) {
        overrides.remove(id);
    }

    /** Seed a flag directly, bypassing the counters/engine. */
    InMemoryFlagStore seed(Flag flag) {
        flags.put(flag.name(), flag);
        return this;
    }
}
