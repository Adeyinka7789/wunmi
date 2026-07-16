package io.github.adeyinka7789.wunmi.spring;

import io.github.adeyinka7789.wunmi.Flag;
import io.github.adeyinka7789.wunmi.FlagCache;
import io.github.adeyinka7789.wunmi.FlagContextResolver;
import io.github.adeyinka7789.wunmi.FlagEngine;
import io.github.adeyinka7789.wunmi.FlagOverride;
import io.github.adeyinka7789.wunmi.FlagStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WunmiAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WunmiAutoConfiguration.class));

    @Test
    void wiresEngineAndDefaults_whenFlagStoreProvided() {
        runner.withBean(FlagStore.class, OneFlagStore::new).run(ctx -> {
            assertThat(ctx).hasSingleBean(FlagEngine.class);
            assertThat(ctx).hasSingleBean(FlagCache.class);
            assertThat(ctx).hasSingleBean(FlagContextResolver.class);
            assertThat(ctx).hasSingleBean(RequiresFlagAspect.class);
            assertThat(ctx.getBean(FlagCache.class)).isInstanceOf(RequestScopedFlagCache.class);
        });
    }

    @Test
    void noEngine_whenNoFlagStore() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(FlagEngine.class));
    }

    @Test
    void engineResolvesFlagThroughStore() {
        runner.withBean(FlagStore.class, OneFlagStore::new)
                .run(ctx -> assertThat(ctx.getBean(FlagEngine.class).isEnabled("DARK_MODE")).isTrue());
    }

    @Test
    void honoursCustomCacheTtlProperty() {
        runner.withPropertyValues("wunmi.cache-ttl-ms=1234")
                .run(ctx -> assertThat(ctx.getBean(WunmiProperties.class).getCacheTtlMs()).isEqualTo(1234));
    }

    /** Minimal store exposing a single enabled flag. */
    static class OneFlagStore implements FlagStore {
        @Override public Optional<Flag> findFlag(String name) {
            return "DARK_MODE".equals(name) ? Optional.of(Flag.enabledFlag("DARK_MODE")) : Optional.empty();
        }
        @Override public List<Flag> findAllFlags() { return List.of(Flag.enabledFlag("DARK_MODE")); }
        @Override public Flag saveFlag(Flag flag) { return flag; }
        @Override public Optional<FlagOverride> findOverride(String f, FlagOverride.Scope s, String v) { return Optional.empty(); }
        @Override public List<FlagOverride> findOverrides(String flagName) { return List.of(); }
        @Override public FlagOverride saveOverride(FlagOverride override) { return override; }
        @Override public Optional<FlagOverride> findOverrideById(UUID id) { return Optional.empty(); }
        @Override public void deleteOverride(UUID id) { }
    }
}
