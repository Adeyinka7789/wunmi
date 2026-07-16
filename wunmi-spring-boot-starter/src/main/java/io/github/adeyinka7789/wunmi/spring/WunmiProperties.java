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

    private final Jdbc jdbc = new Jdbc();

    private final Invalidation invalidation = new Invalidation();

    public long getCacheTtlMs() {
        return cacheTtlMs;
    }

    public void setCacheTtlMs(long cacheTtlMs) {
        this.cacheTtlMs = cacheTtlMs;
    }

    public Jdbc getJdbc() {
        return jdbc;
    }

    public Invalidation getInvalidation() {
        return invalidation;
    }

    /** Settings for the bundled JDBC store (only used when {@code wunmi-jdbc} is on the classpath). */
    public static class Jdbc {

        /**
         * Whether to create the wunmi tables at startup (idempotent {@code CREATE TABLE IF NOT
         * EXISTS}). Off by default — turn on for dev/first run, or manage the schema yourself.
         */
        private boolean initializeSchema = false;

        public boolean isInitializeSchema() {
            return initializeSchema;
        }

        public void setInitializeSchema(boolean initializeSchema) {
            this.initializeSchema = initializeSchema;
        }
    }

    /**
     * Settings for cross-instance cache invalidation — so a flag changed on one instance is seen by
     * the others without waiting out their {@link #getCacheTtlMs() cache TTL}. Backed by the
     * bundled JDBC broadcaster (a polled version counter) when {@code wunmi-jdbc} and a
     * {@code DataSource} are present, unless you declare your own {@code FlagChangeBroadcaster}.
     */
    public static class Invalidation {

        /**
         * Whether to propagate flag changes between instances. On by default; if the
         * {@code wunmi_flag_version} table is unavailable this degrades to a startup warning and
         * TTL-bounded convergence rather than failing. Turn off to skip the poller entirely.
         */
        private boolean enabled = true;

        /**
         * How often (ms) to poll for changes made by other instances. This bounds how long a peer's
         * change takes to be seen here. Default 5000.
         */
        private long pollIntervalMs = 5000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }
    }
}
