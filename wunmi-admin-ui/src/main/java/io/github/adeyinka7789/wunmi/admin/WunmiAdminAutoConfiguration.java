package io.github.adeyinka7789.wunmi.admin;

import io.github.adeyinka7789.wunmi.FlagEngine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Registers the {@link WunmiAdminController} in a servlet web app when a {@link FlagEngine} bean
 * is present. Adding {@code wunmi-admin-ui} to a Spring Boot app that already uses wunmi turns on
 * the console at {@code /wunmi/admin} — remember to secure that path.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
public class WunmiAdminAutoConfiguration {

    @Bean
    @ConditionalOnBean(FlagEngine.class)
    @ConditionalOnMissingBean
    public WunmiAdminController wunmiAdminController(FlagEngine engine) {
        return new WunmiAdminController(engine);
    }
}
