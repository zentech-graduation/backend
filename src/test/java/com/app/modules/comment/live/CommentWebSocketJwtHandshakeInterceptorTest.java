package com.app.modules.comment.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import com.app.common.security.service.TokenPrincipalResolver;
import com.app.common.security.user.UserPrincipal;

@ExtendWith(MockitoExtension.class)
class CommentWebSocketJwtHandshakeInterceptorTest {

    @Mock private TokenPrincipalResolver tokenPrincipalResolver;

    private CommentWebSocketJwtHandshakeInterceptor interceptor;

    private static final String TOKEN = "test-token";

    @BeforeEach
    void setUp() {
        interceptor = new CommentWebSocketJwtHandshakeInterceptor(tokenPrincipalResolver);
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
    void handshake_blacklistedToken_returnsFalse() throws Exception {
        when(tokenPrincipalResolver.resolve(TOKEN)).thenReturn(Optional.empty());

        ServerHttpRequest request = requestWithToken(TOKEN);
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean result = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(result).isFalse();
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
        when(tokenPrincipalResolver.resolve("bad-token")).thenReturn(Optional.empty());

        ServerHttpRequest request = requestWithToken("bad-token");
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean result = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(result).isFalse();
    }

    @Test
    void handshake_nonActiveAccountValidToken_returnsFalse() throws Exception {
        when(tokenPrincipalResolver.resolve(TOKEN)).thenReturn(Optional.empty());

        ServerHttpRequest request = requestWithToken(TOKEN);
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
