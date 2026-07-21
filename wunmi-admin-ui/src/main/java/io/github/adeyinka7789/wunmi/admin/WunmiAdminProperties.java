package io.github.adeyinka7789.wunmi.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the admin console, under {@code wunmi.admin.*}.
 */
@ConfigurationProperties(prefix = "wunmi.admin")
public class WunmiAdminProperties {

    /**
     * When set (and Spring Security is on the classpath), requests to {@code /wunmi/admin/**} must
     * carry this granted authority, else they get {@code 403}. The value is matched both verbatim
     * and with the conventional {@code ROLE_} prefix, so {@code ADMIN} accepts an authority of
     * {@code ADMIN} or {@code ROLE_ADMIN}. Empty/unset (the default) leaves the console ungated —
     * secure the path with your own security config, as before.
     */
    private String requireRole;

    public String getRequireRole() {
        return requireRole;
    }

    public void setRequireRole(String requireRole) {
        this.requireRole = requireRole;
    }
}
