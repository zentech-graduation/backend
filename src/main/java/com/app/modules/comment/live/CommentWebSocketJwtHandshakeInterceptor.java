package com.app.modules.comment.live;

import java.net.URI;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import com.app.common.security.jwt.JwtClaims;
import com.app.common.security.jwt.JwtTokenProvider;
import com.app.common.security.service.TokenBlacklistService;
import com.app.common.security.user.UserPrincipal;

/**
 * Authenticates the WebSocket upgrade using a JWT supplied as the {@code token} query parameter.
 *
 * <p>Browsers cannot set Authorization headers on a WebSocket upgrade, so the access token rides as
 * a query parameter. On success the resolved {@link UserPrincipal} is stored in the handshake
 * attributes under {@code principal} for later per-subscription authorization.
 */
@Component
@ConditionalOnProperty(prefix = "app.comment.live", name = "enabled", havingValue = "true")
public class CommentWebSocketJwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String PRINCIPAL_ATTRIBUTE = "principal";

    private static final Logger log =
            LoggerFactory.getLogger(CommentWebSocketJwtHandshakeInterceptor.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;

    public CommentWebSocketJwtHandshakeInterceptor(
            JwtTokenProvider jwtTokenProvider, TokenBlacklistService tokenBlacklistService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenBlacklistService = tokenBlacklistService;
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
        try {
            JwtClaims claims = jwtTokenProvider.validateAndParse(token);
            if (tokenBlacklistService.isBlacklisted(claims.jti())) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put(
                    PRINCIPAL_ATTRIBUTE,
                    new UserPrincipal(claims.userId(), claims.email(), claims.role(), "ACTIVE"));
            return true;
        } catch (RuntimeException ex) {
            log.debug("Rejected WebSocket handshake: {}", ex.getMessage());
            return false;
        }
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
