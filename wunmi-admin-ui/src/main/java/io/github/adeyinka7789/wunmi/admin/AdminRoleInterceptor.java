package io.github.adeyinka7789.wunmi.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Requires an authenticated caller with a configured authority before any {@code /wunmi/admin/**}
 * request runs, rejecting others with {@code 403}. Registered only when a required role is set (see
 * {@link WunmiAdminProperties#getRequireRole()}) and Spring Security is on the classpath — so this
 * class is loaded lazily and never referenced when Security is absent.
 *
 * <p>It reads the current {@link Authentication} from the {@link SecurityContextHolder} the host's
 * security filter chain has already populated; it does not authenticate on its own. The role is
 * matched verbatim and with the conventional {@code ROLE_} prefix.
 */
class AdminRoleInterceptor implements HandlerInterceptor {

    private final String requiredRole;
    private final String prefixedRole;

    AdminRoleInterceptor(String requiredRole) {
        this.requiredRole = requiredRole;
        this.prefixedRole = requiredRole.startsWith("ROLE_") ? requiredRole : "ROLE_" + requiredRole;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && hasRequiredRole(auth)) {
            return true;
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN,
                "wunmi admin requires role " + requiredRole);
        return false;
    }

    private boolean hasRequiredRole(Authentication auth) {
        for (GrantedAuthority granted : auth.getAuthorities()) {
            String authority = granted.getAuthority();
            if (requiredRole.equals(authority) || prefixedRole.equals(authority)) {
                return true;
            }
        }
        return false;
    }
}
