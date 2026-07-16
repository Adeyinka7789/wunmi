package io.github.adeyinka7789.wunmi.spring;

import io.github.adeyinka7789.wunmi.FlagEngine;
import io.github.adeyinka7789.wunmi.FlagStore;
import io.github.adeyinka7789.wunmi.jdbc.JdbcFlagStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WunmiJdbcAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    WunmiJdbcAutoConfiguration.class, WunmiAutoConfiguration.class))
            .withBean(DataSource.class, WunmiJdbcAutoConfigurationTest::h2);

    @Test
    void autoConfiguresJdbcStoreAndEngine_whenDataSourcePresent() {
        runner.withPropertyValues("wunmi.jdbc.initialize-schema=true").run(ctx -> {
            assertThat(ctx).hasSingleBean(FlagStore.class);
            assertThat(ctx.getBean(FlagStore.class)).isInstanceOf(JdbcFlagStore.class);
            assertThat(ctx).hasSingleBean(FlagEngine.class);

            FlagEngine engine = ctx.getBean(FlagEngine.class);
            engine.enable("DARK_MODE", "admin");
            assertThat(engine.isEnabled("DARK_MODE")).isTrue();
        });
    }

    @Test
    void backsOff_whenAppProvidesItsOwnFlagStore() {
        runner.withBean("customStore", FlagStore.class, () -> mock(FlagStore.class)).run(ctx -> {
            assertThat(ctx).hasSingleBean(FlagStore.class);
            assertThat(ctx.getBean(FlagStore.class)).isNotInstanceOf(JdbcFlagStore.class);
        });
    }

    private static DataSource h2() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:starter_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        return ds;
    }
}
