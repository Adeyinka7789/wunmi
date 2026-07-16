package io.github.adeyinka7789.wunmi.spring;

import io.github.adeyinka7789.wunmi.FlagStore;
import io.github.adeyinka7789.wunmi.jdbc.JdbcFlagStore;
import io.github.adeyinka7789.wunmi.jdbc.WunmiSchema;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * When {@code wunmi-jdbc} is on the classpath and a {@link DataSource} bean exists, provides a
 * {@link JdbcFlagStore} as the default {@link FlagStore} — so a Spring Boot app gets flag
 * persistence with zero code (just a datasource). Declaring your own {@code FlagStore} bean
 * overrides this. Set {@code wunmi.jdbc.initialize-schema=true} to create the tables at startup.
 *
 * <p>Ordered before {@link WunmiAutoConfiguration} so its {@code FlagStore} is registered in time
 * for the {@code FlagEngine}'s {@code @ConditionalOnBean(FlagStore.class)}.
 */
@AutoConfiguration(before = WunmiAutoConfiguration.class)
@ConditionalOnClass(JdbcFlagStore.class)
@EnableConfigurationProperties(WunmiProperties.class)
public class WunmiJdbcAutoConfiguration {

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(FlagStore.class)
    public FlagStore wunmiJdbcFlagStore(DataSource dataSource, WunmiProperties properties) {
        if (properties.getJdbc().isInitializeSchema()) {
            WunmiSchema.initialize(dataSource);
        }
        return new JdbcFlagStore(dataSource);
    }
}
