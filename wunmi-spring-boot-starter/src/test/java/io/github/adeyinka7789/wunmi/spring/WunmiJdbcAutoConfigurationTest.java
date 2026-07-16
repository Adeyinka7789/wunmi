package io.github.adeyinka7789.wunmi.spring;

import io.github.adeyinka7789.wunmi.FlagChangeBroadcaster;
import io.github.adeyinka7789.wunmi.FlagEngine;
import io.github.adeyinka7789.wunmi.FlagStore;
import io.github.adeyinka7789.wunmi.jdbc.JdbcFlagChangeBroadcaster;
import io.github.adeyinka7789.wunmi.jdbc.JdbcFlagStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    @Test
    void autoConfiguresJdbcBroadcaster_whenDataSourcePresent() {
        runner.withPropertyValues("wunmi.jdbc.initialize-schema=true").run(ctx -> {
            assertThat(ctx).hasSingleBean(FlagChangeBroadcaster.class);
            assertThat(ctx.getBean(FlagChangeBroadcaster.class)).isInstanceOf(JdbcFlagChangeBroadcaster.class);
        });
    }

    @Test
    void startsWithoutTheVersionTable_whenSchemaIsAppManaged() {
        // initialize-schema defaults off: the counter table won't exist. Startup must still succeed.
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(FlagChangeBroadcaster.class);
        });
    }

    @Test
    void backsOff_whenInvalidationDisabled() {
        runner.withPropertyValues("wunmi.invalidation.enabled=false").run(ctx -> {
            assertThat(ctx).doesNotHaveBean(FlagChangeBroadcaster.class);
            assertThat(ctx).hasSingleBean(FlagEngine.class);   // engine still wires, with NONE
        });
    }

    @Test
    void backsOff_whenAppProvidesItsOwnBroadcaster() {
        runner.withBean("customBroadcaster", FlagChangeBroadcaster.class, () -> mock(FlagChangeBroadcaster.class))
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(FlagChangeBroadcaster.class);
                    assertThat(ctx.getBean(FlagChangeBroadcaster.class))
                            .isNotInstanceOf(JdbcFlagChangeBroadcaster.class);
                });
    }

    /** End-to-end through the autoconfig: a flag change is picked up by the started poller. */
    @Test
    void engineChangeIsObservedByThePoller() {
        runner.withPropertyValues("wunmi.jdbc.initialize-schema=true",
                        "wunmi.invalidation.poll-interval-ms=20")
                .run(ctx -> {
                    CountDownLatch observed = new CountDownLatch(1);
                    ctx.getBean(FlagChangeBroadcaster.class).addListener(observed::countDown);

                    ctx.getBean(FlagEngine.class).enable("DARK_MODE", "admin");

                    assertThat(observed.await(5, TimeUnit.SECONDS))
                            .as("the poller should see the broadcast version bump")
                            .isTrue();
                });
    }

    private static DataSource h2() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:starter_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        return ds;
    }
}
