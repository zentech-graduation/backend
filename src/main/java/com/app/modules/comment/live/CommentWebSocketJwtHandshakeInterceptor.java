package com.app.modules.comment.live;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

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
import com.app.common.security.service.TokenPrincipalResolver;
import com.app.common.security.user.UserPrincipal;

/**
 * Authenticates the WebSocket upgrade using a JWT supplied as the {@code token} query parameter,
 * delegating the same signature/expiry/blacklist/account-status checks the REST path enforces to
 * {@link TokenPrincipalResolver}.
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

    private final TokenPrincipalResolver tokenPrincipalResolver;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;

    public CommentWebSocketJwtHandshakeInterceptor(
            TokenPrincipalResolver tokenPrincipalResolver,
            JwtTokenProvider jwtTokenProvider,
            TokenBlacklistService tokenBlacklistService) {
        this.tokenPrincipalResolver = tokenPrincipalResolver;
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
        Optional<UserPrincipal> principal = tokenPrincipalResolver.resolve(token);
        if (principal.isEmpty()) {
            if (isBlacklistedButOtherwiseValid(token)) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
            }
            return false;
        }
        attributes.put(PRINCIPAL_ATTRIBUTE, principal.get());
        return true;
    }

    // Preserves the pre-existing 401-on-blacklist status: TokenPrincipalResolver deliberately does
    // not expose which rejection reason applied, so the one caller that cares re-checks narrowly.
    private boolean isBlacklistedButOtherwiseValid(String token) {
        try {
            JwtClaims claims = jwtTokenProvider.validateAndParse(token);
            return tokenBlacklistService.isBlacklisted(claims.jti());
        } catch (RuntimeException ex) {
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
