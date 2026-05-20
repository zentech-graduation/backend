package com.app.common.security.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import com.app.common.ApiConstants;
import com.app.common.config.redis.RateLimitProperties;
import com.app.common.config.redis.RateLimitProperties.Rule;
import com.app.common.config.security.SecurityProperties;
import com.app.common.security.service.RateLimiterService;
import com.app.common.security.util.IpExtractor;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AuthRateLimitFilterTest {

    private static final Rule LOW_TRAFFIC_RULE = new Rule(5, 60);
    private static final Rule FORGOT_RULE = new Rule(3, 300);
    private static final Rule LOGIN_RULE = new Rule(10, 900);
    private static final Rule RESEND_RULE = new Rule(3, 3600);

    private static final String REGISTER_PATH = ApiConstants.Auth.ROOT + ApiConstants.Auth.REGISTER;
    private static final String LOGIN_PATH = ApiConstants.Auth.ROOT + ApiConstants.Auth.LOGIN;
    private static final String REFRESH_PATH = ApiConstants.Auth.ROOT + ApiConstants.Auth.REFRESH;
    private static final String FORGOT_PATH =
            ApiConstants.Auth.ROOT + ApiConstants.Auth.FORGOT_PASSWORD;
    private static final String RESET_PATH =
            ApiConstants.Auth.ROOT + ApiConstants.Auth.RESET_PASSWORD;
    private static final String VERIFY_PATH =
            ApiConstants.Auth.ROOT + ApiConstants.Auth.VERIFY_EMAIL;
    private static final String RESEND_PATH =
            ApiConstants.Auth.ROOT + ApiConstants.Auth.RESEND_VERIFY;

    @Mock private RateLimiterService rateLimiterService;
    @Mock private IpExtractor ipExtractor;
    @Mock private HttpServletRequest request;
    @Mock private FilterChain chain;

    private RateLimitProperties properties;
    private AuthRateLimitFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties =
                new RateLimitProperties(
                        LOGIN_RULE,
                        FORGOT_RULE,
                        RESEND_RULE,
                        Map.of(
                                REGISTER_PATH, LOW_TRAFFIC_RULE,
                                REFRESH_PATH, new Rule(30, 60),
                                RESET_PATH, new Rule(5, 300),
                                VERIFY_PATH, new Rule(10, 60)));
        SecurityProperties securityProperties = new SecurityProperties(java.util.List.of(), 2048);
        filter =
                new AuthRateLimitFilter(
                        rateLimiterService,
                        properties,
                        objectMapper,
                        ipExtractor,
                        securityProperties);
    }

    @Test
    void isRateLimited_register_post_returnsTrue() {
        assertThat(filter.isRateLimited(REGISTER_PATH, "POST")).isTrue();
    }

    @Test
    void isRateLimited_refresh_post_returnsTrue() {
        assertThat(filter.isRateLimited(REFRESH_PATH, "POST")).isTrue();
    }

    @Test
    void isRateLimited_resetPassword_post_returnsTrue() {
        assertThat(filter.isRateLimited(RESET_PATH, "POST")).isTrue();
    }

    @Test
    void isRateLimited_verifyEmail_get_returnsTrue() {
        assertThat(filter.isRateLimited(VERIFY_PATH, "GET")).isTrue();
    }

    @Test
    void isRateLimited_login_post_returnsTrue() {
        assertThat(filter.isRateLimited(LOGIN_PATH, "POST")).isTrue();
    }

    @Test
    void isRateLimited_forgotPassword_post_returnsTrue() {
        assertThat(filter.isRateLimited(FORGOT_PATH, "POST")).isTrue();
    }

    @Test
    void isRateLimited_resendVerify_post_returnsTrue() {
        assertThat(filter.isRateLimited(RESEND_PATH, "POST")).isTrue();
    }

    @Test
    void isRateLimited_logout_post_returnsFalse() {
        String logoutPath = ApiConstants.Auth.ROOT + ApiConstants.Auth.LOGOUT;
        assertThat(filter.isRateLimited(logoutPath, "POST")).isFalse();
    }

    @Test
    void isRateLimited_usersMePath_get_returnsFalse() {
        String mePath = ApiConstants.Users.ROOT + ApiConstants.Users.ME;
        assertThat(filter.isRateLimited(mePath, "GET")).isFalse();
    }

    @Test
    void doFilterInternal_limitExceeded_writesRetryAfterHeader() throws Exception {
        when(request.getRequestURI()).thenReturn(FORGOT_PATH);
        when(request.getMethod()).thenReturn("POST");
        when(ipExtractor.extract(any())).thenReturn("1.2.3.4");
        when(rateLimiterService.isAllowed(anyString(), anyInt(), anyLong())).thenReturn(false);

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        String retryAfter = response.getHeader(HttpHeaders.RETRY_AFTER);
        assertThat(retryAfter).isNotNull();
        assertThat(Long.parseLong(retryAfter)).isGreaterThan(0);
    }

    @Test
    void doFilterInternal_limitExceeded_forRegister_writesRetryAfterHeader() throws Exception {
        when(request.getRequestURI()).thenReturn(REGISTER_PATH);
        when(request.getMethod()).thenReturn("POST");
        when(ipExtractor.extract(any())).thenReturn("1.2.3.4");
        when(rateLimiterService.isAllowed(anyString(), anyInt(), anyLong())).thenReturn(false);

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        String retryAfter = response.getHeader(HttpHeaders.RETRY_AFTER);
        assertThat(retryAfter).isNotNull();
        assertThat(Long.parseLong(retryAfter)).isGreaterThan(0);
    }

    @Test
    void doFilterInternal_underLimit_proceedsToChain() throws Exception {
        when(request.getRequestURI()).thenReturn(FORGOT_PATH);
        when(request.getMethod()).thenReturn("POST");
        when(ipExtractor.extract(any())).thenReturn("1.2.3.4");
        when(rateLimiterService.isAllowed(anyString(), anyInt(), anyLong())).thenReturn(true);

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
