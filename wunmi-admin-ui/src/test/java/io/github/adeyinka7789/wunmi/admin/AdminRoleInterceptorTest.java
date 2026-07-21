package io.github.adeyinka7789.wunmi.admin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static org.assertj.core.api.Assertions.assertThat;

class AdminRoleInterceptorTest {

    private final AdminRoleInterceptor interceptor = new AdminRoleInterceptor("ADMIN");

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsWhenNoAuthentication() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean proceed = interceptor.preHandle(new MockHttpServletRequest(), response, new Object());
        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(SC_FORBIDDEN);
    }

    @Test
    void rejectsWhenRoleMissing() throws Exception {
        authenticateWith("ROLE_USER");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(new MockHttpServletRequest(), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(SC_FORBIDDEN);
    }

    @Test
    void allowsWithPrefixedRole() throws Exception {
        authenticateWith("ROLE_ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(new MockHttpServletRequest(), response, new Object())).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void allowsWithVerbatimRole() throws Exception {
        authenticateWith("ADMIN");
        assertThat(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object()))
                .isTrue();
    }

    private void authenticateWith(String authority) {
        var auth = new UsernamePasswordAuthenticationToken(
                "alice", "n/a", List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
