package io.github.adeyinka7789.wunmi.demo;

import io.github.adeyinka7789.wunmi.FlagContext;
import io.github.adeyinka7789.wunmi.FlagContextResolver;
import io.github.adeyinka7789.wunmi.FlagEngine;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Demo wiring: how the current subject is resolved, and some seed data so the app is useful the
 * moment it boots.
 */
@Configuration
public class DemoConfig {

    /**
     * Tells wunmi who is asking, so subject overrides and rollout bucketing work for the plain
     * {@link FlagEngine#isOn} calls. Here we read demo headers off the current request; a real app
     * would read its security context. When there's no request bound, resolution is global-only.
     */
    @Bean
    FlagContextResolver flagContextResolver() {
        return () -> {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                String userId = attrs.getRequest().getHeader("X-User-Id");
                String plan = attrs.getRequest().getHeader("X-User-Plan");
                if (userId != null || plan != null) {
                    return new FlagContext(userId, plan);
                }
            }
            return FlagContext.EMPTY;
        };
    }

    /** Seed two flags at startup: one fully on, one rolled out to half of subjects. */
    @Bean
    CommandLineRunner seedFlags(FlagEngine flags) {
        return args -> {
            flags.enable(DemoFlags.NEW_DASHBOARD.key(), "demo-seed");
            flags.enable(DemoFlags.BETA_EXPORT.key(), "demo-seed");
            flags.setRollout(DemoFlags.BETA_EXPORT.key(), 50, "demo-seed");
        };
    }
}
