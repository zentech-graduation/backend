package com.app.modules.comment.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.security.jwt.JwtClaims;
import com.app.common.security.jwt.JwtTokenProvider;
import com.app.common.security.service.TokenBlacklistService;
import com.app.common.security.service.TokenPrincipalResolver;
import com.app.common.security.user.UserPrincipal;

@ExtendWith(MockitoExtension.class)
class CommentWebSocketJwtHandshakeInterceptorTest {

    @Mock private TokenPrincipalResolver tokenPrincipalResolver;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private TokenBlacklistService tokenBlacklistService;

    private CommentWebSocketJwtHandshakeInterceptor interceptor;

    private static final String JTI = UUID.randomUUID().toString();
    private static final String TOKEN = "test-token";

    @BeforeEach
    void setUp() {
        interceptor =
                new CommentWebSocketJwtHandshakeInterceptor(
                        tokenPrincipalResolver, jwtTokenProvider, tokenBlacklistService);
    }

    @Test
    void handshake_resolvedPrincipal_returnsTrue() throws Exception {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), null, "USER", "ACTIVE");
        when(tokenPrincipalResolver.resolve(TOKEN)).thenReturn(Optional.of(principal));

        ServerHttpRequest request = requestWithToken(TOKEN);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        Map<String, Object> attrs = new HashMap<>();

        boolean result = interceptor.beforeHandshake(request, response, null, attrs);

        assertThat(result).isTrue();
        assertThat(attrs)
                .containsEntry(
                        CommentWebSocketJwtHandshakeInterceptor.PRINCIPAL_ATTRIBUTE, principal);
    }

    @Test
    void handshake_blacklistedToken_returnsFalseAndSetsUnauthorized() throws Exception {
        JwtClaims claims = new JwtClaims(UUID.randomUUID(), "user", JTI, Instant.now());
        when(tokenPrincipalResolver.resolve(TOKEN)).thenReturn(Optional.empty());
        when(jwtTokenProvider.validateAndParse(TOKEN)).thenReturn(claims);
        when(tokenBlacklistService.isBlacklisted(JTI)).thenReturn(true);

        ServerHttpRequest request = requestWithToken(TOKEN);
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean result = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void handshake_missingTokenParam_returnsFalse() throws Exception {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws/comments"));
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean result = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(result).isFalse();
    }

    @Test
    void handshake_invalidToken_returnsFalseWithoutSettingStatus() throws Exception {
        when(tokenPrincipalResolver.resolve("bad-token")).thenReturn(Optional.empty());
        when(jwtTokenProvider.validateAndParse(anyString()))
                .thenThrow(new AppException(ApiErrorCode.AUTH_TOKEN_INVALID));

        ServerHttpRequest request = requestWithToken("bad-token");
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean result = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(result).isFalse();
        verify(response, never()).setStatusCode(any());
    }

    @Test
    void handshake_nonActiveAccountValidToken_returnsFalseWithoutSettingStatus() throws Exception {
        JwtClaims claims = new JwtClaims(UUID.randomUUID(), "user", JTI, Instant.now());
        when(tokenPrincipalResolver.resolve(TOKEN)).thenReturn(Optional.empty());
        when(jwtTokenProvider.validateAndParse(TOKEN)).thenReturn(claims);
        when(tokenBlacklistService.isBlacklisted(JTI)).thenReturn(false);

        ServerHttpRequest request = requestWithToken(TOKEN);
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean result = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(result).isFalse();
        verify(response, never()).setStatusCode(any());
    }

    private static ServerHttpRequest requestWithToken(String token) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws/comments?token=" + token));
        return request;
    }
}
