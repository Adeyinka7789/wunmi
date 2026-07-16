package io.github.adeyinka7789.wunmi.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the wunmi Spring Boot integration, under the {@code wunmi.*} prefix.
 */
@ConfigurationProperties(prefix = "wunmi")
public class WunmiProperties {

    /**
     * How long (ms) the flag/override view is cached outside an HTTP request (jobs, schedulers).
     * A flag change propagates to those contexts within this window. Inside a request, caching is
     * request-scoped and this does not apply. Default 5000.
     */
    private long cacheTtlMs = 5000;

    public long getCacheTtlMs() {
        return cacheTtlMs;
    }

    public void setCacheTtlMs(long cacheTtlMs) {
        this.cacheTtlMs = cacheTtlMs;
    }
}
