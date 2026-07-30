package com.app.common.security.websocket;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import com.app.common.security.service.TokenPrincipalResolver;
import com.app.common.security.user.UserPrincipal;

/**
 * Authenticates a WebSocket upgrade using a JWT supplied as the {@code token} query parameter,
 * delegating the same signature/expiry/blacklist/account-status checks the REST path enforces to
 * {@link TokenPrincipalResolver}.
 *
 * <p>Browsers cannot set Authorization headers on a WebSocket upgrade, so the access token rides as
 * a query parameter. On success the resolved {@link UserPrincipal} is stored in the handshake
 * attributes under {@code principal} for later per-subscription authorization. Shared by every
 * STOMP endpoint in the application so the WebSocket path cannot drift from the REST path on what
 * counts as authenticated.
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String PRINCIPAL_ATTRIBUTE = "principal";

    private final TokenPrincipalResolver tokenPrincipalResolver;

    public JwtHandshakeInterceptor(TokenPrincipalResolver tokenPrincipalResolver) {
        this.tokenPrincipalResolver = tokenPrincipalResolver;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        String token = extractToken(request.getURI());
        if (token == null || token.isBlank()) {
            return false;
        }
        Optional<UserPrincipal> principal = tokenPrincipalResolver.resolve(token);
        if (principal.isEmpty()) {
            return false;
        }
        attributes.put(PRINCIPAL_ATTRIBUTE, principal.get());
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // No post-handshake action required.
    }

    private static String extractToken(URI uri) {
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("token");
    }
}
