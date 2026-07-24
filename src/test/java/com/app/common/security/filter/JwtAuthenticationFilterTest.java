package com.app.common.security.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import com.app.common.security.service.TokenPrincipalResolver;
import com.app.common.security.user.UserPrincipal;

/**
 * {@link TokenPrincipalResolver} owns every rejection reason (banned, suspended, deactivated,
 * blacklisted, malformed/expired token, user not found); see {@code TokenPrincipalResolverImplTest}
 * for that coverage. This class only proves the filter reacts correctly to what the resolver
 * returns: authenticate on a resolved principal, clear the context on empty, and always continue
 * the chain exactly once.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String TOKEN = "header.payload.signature";
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private TokenPrincipalResolver tokenPrincipalResolver;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(tokenPrincipalResolver);
        SecurityContextHolder.clearContext();
        when(request.getHeader("Authorization")).thenReturn("Bearer " + TOKEN);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvedPrincipal_populatesSecurityContext() throws Exception {
        UserPrincipal principal = new UserPrincipal(USER_ID, "user@example.com", "USER", "ACTIVE");
        when(tokenPrincipalResolver.resolve(TOKEN)).thenReturn(Optional.of(principal));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(principal);
        verify(chain).doFilter(request, response);
    }

    @Test
    void unresolvedPrincipal_leavesSecurityContextEmpty() throws Exception {
        when(tokenPrincipalResolver.resolve(TOKEN)).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void noAuthorizationHeader_skipsResolutionAndContinuesChain() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }
}
