package io.github.adeyinka7789.wunmi.spring;

import io.github.adeyinka7789.wunmi.FlagAuditListener;
import io.github.adeyinka7789.wunmi.FlagCache;
import io.github.adeyinka7789.wunmi.FlagChangeBroadcaster;
import io.github.adeyinka7789.wunmi.FlagContextResolver;
import io.github.adeyinka7789.wunmi.FlagEngine;
import io.github.adeyinka7789.wunmi.FlagEvaluationListener;
import io.github.adeyinka7789.wunmi.FlagStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for wunmi. Supply a {@link FlagStore} bean and you get a fully wired
 * {@link FlagEngine}; everything else has a sensible default you can override by declaring your
 * own bean:
 *
 * <ul>
 *   <li>{@link FlagCache} → {@link RequestScopedFlagCache} (request-scoped + short TTL)</li>
 *   <li>{@link FlagContextResolver} → {@link FlagContextResolver#EMPTY} (global resolution only —
 *       declare your own to enable per-subject/segment overrides and rollout)</li>
 *   <li>{@link FlagAuditListener} → {@link FlagAuditListener#NOOP}</li>
 *   <li>{@link FlagEvaluationListener} → {@link FlagEvaluationListener#NOOP} (declare your own —
 *       e.g. a Micrometer adapter — to record per-evaluation metrics)</li>
 *   <li>{@link FlagChangeBroadcaster} → the bundled JDBC one when {@code wunmi-jdbc} and a
 *       {@code DataSource} are present (see {@link WunmiJdbcAutoConfiguration}), otherwise
 *       {@link FlagChangeBroadcaster#NONE} — declare your own for Redis/Kafka fan-out</li>
 * </ul>
 *
 * The {@link RequiresFlagAspect} is registered so {@link RequiresFlag} works out of the box.
 */
@AutoConfiguration
@EnableConfigurationProperties(WunmiProperties.class)
public class WunmiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FlagCache wunmiFlagCache(WunmiProperties properties) {
        return new RequestScopedFlagCache(properties.getCacheTtlMs());
    }

    @Bean
    @ConditionalOnMissingBean
    public FlagContextResolver wunmiFlagContextResolver() {
        return FlagContextResolver.EMPTY;
    }

    @Bean
    @ConditionalOnMissingBean
    public FlagAuditListener wunmiFlagAuditListener() {
        return FlagAuditListener.NOOP;
    }

    @Bean
    @ConditionalOnMissingBean
    public FlagEvaluationListener wunmiFlagEvaluationListener() {
        return FlagEvaluationListener.NOOP;
    }

    @Bean
    @ConditionalOnBean(FlagStore.class)
    @ConditionalOnMissingBean
    public FlagEngine flagEngine(FlagStore store, FlagCache cache,
                                 FlagAuditListener audit, FlagContextResolver contextResolver,
                                 ObjectProvider<FlagChangeBroadcaster> broadcaster,
                                 FlagEvaluationListener evaluationListener) {
        return new FlagEngine(store, cache, audit, contextResolver,
                broadcaster.getIfAvailable(() -> FlagChangeBroadcaster.NONE), evaluationListener);
    }

    @Bean
    @ConditionalOnBean(FlagEngine.class)
    @ConditionalOnMissingBean
    public RequiresFlagAspect requiresFlagAspect(FlagEngine flagEngine) {
        return new RequiresFlagAspect(flagEngine);
    }
}
