package io.github.adeyinka7789.wunmi.spring;

import io.github.adeyinka7789.wunmi.Flag;
import io.github.adeyinka7789.wunmi.FlagContextResolver;
import io.github.adeyinka7789.wunmi.FlagDisabledException;
import io.github.adeyinka7789.wunmi.FlagEngine;
import io.github.adeyinka7789.wunmi.FlagOverride;
import io.github.adeyinka7789.wunmi.FlagOverride.Scope;
import io.github.adeyinka7789.wunmi.FlagStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequiresFlagAspectTest {

    private AnnotationConfigApplicationContext context;
    private Service service;
    private MapFlagStore store;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        service = context.getBean(Service.class);
        store = context.getBean(MapFlagStore.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void spelSubject_offForOverriddenSubject_onForOthers() {
        store.seed(Flag.enabledFlag("BETA"));
        store.saveOverride(FlagOverride.create("BETA", Scope.SUBJECT, "user-1", false, null, "admin"));

        assertThatThrownBy(() -> service.checkoutForSubject("user-1"))
                .isInstanceOf(FlagDisabledException.class);
        assertThat(service.checkoutForSubject("user-2")).isEqualTo("ok");
    }

    @Test
    void spelSegment_offForOverriddenSegment() {
        store.seed(Flag.enabledFlag("BETA"));
        store.saveOverride(FlagOverride.create("BETA", Scope.SEGMENT, "free", false, null, "admin"));

        assertThatThrownBy(() -> service.checkoutForSegment("free"))
                .isInstanceOf(FlagDisabledException.class);
        assertThat(service.checkoutForSegment("pro")).isEqualTo("ok");
    }

    @Test
    void noSpel_usesAmbientContextResolver() {
        store.seed(Flag.enabledFlag("BETA"));
        store.saveOverride(FlagOverride.create("BETA", Scope.SUBJECT, "ambient-user", false, null, "admin"));

        // The resolver bean binds subject "ambient-user", so the plain gate resolves off.
        assertThatThrownBy(() -> service.checkoutAmbient())
                .isInstanceOf(FlagDisabledException.class);
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {
        @Bean MapFlagStore flagStore() { return new MapFlagStore(); }
        @Bean FlagContextResolver contextResolver() {
            return () -> io.github.adeyinka7789.wunmi.FlagContext.ofSubject("ambient-user");
        }
        @Bean FlagEngine flagEngine(FlagStore store, FlagContextResolver resolver) {
            return new FlagEngine(store, io.github.adeyinka7789.wunmi.FlagCache.NONE,
                    io.github.adeyinka7789.wunmi.FlagAuditListener.NOOP, resolver);
        }
        @Bean RequiresFlagAspect aspect(FlagEngine engine) { return new RequiresFlagAspect(engine); }
        @Bean Service service() { return new Service(); }
    }

    static class Service {
        @RequiresFlag(value = "BETA", subject = "#userId")
        String checkoutForSubject(String userId) { return "ok"; }

        @RequiresFlag(value = "BETA", segment = "#plan")
        String checkoutForSegment(String plan) { return "ok"; }

        @RequiresFlag("BETA")
        String checkoutAmbient() { return "ok"; }
    }

    /** Minimal in-memory store for the aspect tests. */
    static class MapFlagStore implements FlagStore {
        private final List<Flag> flags = new ArrayList<>();
        private final List<FlagOverride> overrides = new ArrayList<>();

        void seed(Flag flag) { flags.add(flag); }

        @Override public Optional<Flag> findFlag(String name) {
            return flags.stream().filter(f -> f.name().equals(name)).findFirst();
        }
        @Override public List<Flag> findAllFlags() { return List.copyOf(flags); }
        @Override public Flag saveFlag(Flag flag) {
            flags.removeIf(f -> f.name().equals(flag.name()));
            flags.add(flag);
            return flag;
        }
        @Override public Optional<FlagOverride> findOverride(String flagName, Scope scope, String value) {
            return overrides.stream()
                    .filter(o -> o.flagName().equals(flagName) && o.scope() == scope && o.value().equals(value))
                    .findFirst();
        }
        @Override public List<FlagOverride> findOverrides(String flagName) {
            return overrides.stream().filter(o -> o.flagName().equals(flagName)).toList();
        }
        @Override public FlagOverride saveOverride(FlagOverride override) {
            FlagOverride withId = override.id() == null
                    ? new FlagOverride(UUID.randomUUID(), override.flagName(), override.scope(),
                        override.value(), override.enabled(), override.reason(), override.createdBy())
                    : override;
            overrides.add(withId);
            return withId;
        }
        @Override public Optional<FlagOverride> findOverrideById(UUID id) {
            return overrides.stream().filter(o -> id.equals(o.id())).findFirst();
        }
        @Override public void deleteOverride(UUID id) {
            overrides.removeIf(o -> id.equals(o.id()));
        }
    }
}
