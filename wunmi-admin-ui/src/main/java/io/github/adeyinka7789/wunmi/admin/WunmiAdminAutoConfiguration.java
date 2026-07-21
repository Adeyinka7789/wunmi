package io.github.adeyinka7789.wunmi.admin;

import io.github.adeyinka7789.wunmi.FlagEngine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link WunmiAdminController} in a servlet web app when a {@link FlagEngine} bean
 * is present. Adding {@code wunmi-admin-ui} to a Spring Boot app that already uses wunmi turns on
 * the console at {@code /wunmi/admin}.
 *
 * <p>The console performs no authorization by default — secure {@code /wunmi/admin/**} with your
 * own security config. For a quick built-in gate, set {@code wunmi.admin.require-role}: when Spring
 * Security is on the classpath, an interceptor then requires that authority on every admin request
 * (see {@link AdminRoleInterceptor}).
 */
@AutoConfiguration(afterName = "io.github.adeyinka7789.wunmi.spring.WunmiAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
@EnableConfigurationProperties(WunmiAdminProperties.class)
public class WunmiAdminAutoConfiguration {

    @Bean
    @ConditionalOnBean(FlagEngine.class)
    @ConditionalOnMissingBean
    public WunmiAdminController wunmiAdminController(FlagEngine engine) {
        return new WunmiAdminController(engine);
    }

    /**
     * Path-based role gate for the console. Active only when {@code wunmi.admin.require-role} is set
     * and Spring Security is present; otherwise the console stays ungated and this class is never
     * loaded.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
    @ConditionalOnProperty(prefix = "wunmi.admin", name = "require-role")
    static class AdminSecurityConfiguration implements WebMvcConfigurer {

        private final WunmiAdminProperties properties;

        AdminSecurityConfiguration(WunmiAdminProperties properties) {
            this.properties = properties;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new AdminRoleInterceptor(properties.getRequireRole()))
                    .addPathPatterns(WunmiAdminController.BASE_PATH, WunmiAdminController.BASE_PATH + "/**");
        }
    }
}
