package com.app.common.security.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
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

import com.app.common.security.jwt.JwtClaims;
import com.app.common.security.jwt.JwtTokenProvider;
import com.app.common.security.service.TokenBlacklistService;
import com.app.common.security.user.SecurityMapper;
import com.app.common.security.user.UserPrincipal;
import com.app.modules.users.enums.UserStatus;
import com.app.modules.users.repository.UserRepository;
import com.app.modules.users.repository.UserSecurityProjection;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String TOKEN = "header.payload.signature";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String JTI = UUID.randomUUID().toString();

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private UserRepository userRepository;
    @Mock private SecurityMapper securityMapper;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter =
                new JwtAuthenticationFilter(
                        jwtTokenProvider, userRepository, securityMapper, tokenBlacklistService);
        SecurityContextHolder.clearContext();

        JwtClaims claims =
                new JwtClaims(
                        USER_ID, "user@example.com", "USER", JTI, Instant.now().plusSeconds(300));
        when(request.getHeader("Authorization")).thenReturn("Bearer " + TOKEN);
        when(jwtTokenProvider.validateAndParse(TOKEN)).thenReturn(claims);
        when(tokenBlacklistService.isBlacklisted(JTI)).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeUser_populatesSecurityContext() throws Exception {
        UserSecurityProjection user = buildProjection(UserStatus.ACTIVE);
        UserPrincipal principal = new UserPrincipal(USER_ID, "user@example.com", "USER", "ACTIVE");
        when(userRepository.findProjectedByIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.of(user));
        when(securityMapper.toUserPrincipal(user)).thenReturn(principal);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(principal);
        verify(chain).doFilter(request, response);
    }

    @Test
    void bannedUser_leavesSecurityContextEmpty() throws Exception {
        UserSecurityProjection user = buildProjection(UserStatus.BANNED);
        when(userRepository.findProjectedByIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.of(user));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(securityMapper, never()).toUserPrincipal(any(UserSecurityProjection.class));
        verify(chain).doFilter(request, response);
    }

    @Test
    void suspendedUser_leavesSecurityContextEmpty() throws Exception {
        UserSecurityProjection user = buildProjection(UserStatus.SUSPENDED);
        when(userRepository.findProjectedByIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.of(user));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(securityMapper, never()).toUserPrincipal(any(UserSecurityProjection.class));
        verify(chain).doFilter(request, response);
    }

    @Test
    void deactivatedUser_leavesSecurityContextEmpty() throws Exception {
        UserSecurityProjection user = buildProjection(UserStatus.DEACTIVATED);
        when(userRepository.findProjectedByIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.of(user));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(securityMapper, never()).toUserPrincipal(any(UserSecurityProjection.class));
        verify(chain).doFilter(request, response);
    }

    private static UserSecurityProjection buildProjection(UserStatus status) {
        UserSecurityProjection projection = mock(UserSecurityProjection.class);
        when(projection.getStatus()).thenReturn(status);
        return projection;
    }
}
