package com.app.modules.comment.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
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

@ExtendWith(MockitoExtension.class)
class CommentWebSocketJwtHandshakeInterceptorTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private TokenBlacklistService tokenBlacklistService;

    private CommentWebSocketJwtHandshakeInterceptor interceptor;

    private static final String JTI = UUID.randomUUID().toString();
    private static final String TOKEN = "test-token";

    @BeforeEach
    void setUp() {
        interceptor =
                new CommentWebSocketJwtHandshakeInterceptor(
                        jwtTokenProvider, tokenBlacklistService);
    }

    @Test
    void handshake_validNonBlacklistedToken_returnsTrue() throws Exception {
        JwtClaims claims =
                new JwtClaims(UUID.randomUUID(), "u@test.com", "user", JTI, Instant.now());
        when(jwtTokenProvider.validateAndParse(TOKEN)).thenReturn(claims);
        when(tokenBlacklistService.isBlacklisted(JTI)).thenReturn(false);

        ServerHttpRequest request = requestWithToken(TOKEN);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        Map<String, Object> attrs = new HashMap<>();

        boolean result = interceptor.beforeHandshake(request, response, null, attrs);

        assertThat(result).isTrue();
        assertThat(attrs).containsKey(CommentWebSocketJwtHandshakeInterceptor.PRINCIPAL_ATTRIBUTE);
    }

    @Test
    void handshake_blacklistedToken_returnsFalse() throws Exception {
        JwtClaims claims =
                new JwtClaims(UUID.randomUUID(), "u@test.com", "user", JTI, Instant.now());
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
    void handshake_invalidToken_returnsFalse() throws Exception {
        when(jwtTokenProvider.validateAndParse(anyString()))
                .thenThrow(new AppException(ApiErrorCode.AUTH_TOKEN_INVALID));

        ServerHttpRequest request = requestWithToken("bad-token");
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean result = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(result).isFalse();
    }

    private static ServerHttpRequest requestWithToken(String token) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws/comments?token=" + token));
        return request;
    }
}
