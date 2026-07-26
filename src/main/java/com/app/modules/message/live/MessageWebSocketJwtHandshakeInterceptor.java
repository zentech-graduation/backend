package com.app.modules.message.live;

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
import com.app.common.security.user.UserPrincipal;
import com.app.modules.message.repository.MessageUserRepository;
import com.app.modules.users.entity.User;
import com.app.modules.users.enums.UserStatus;

/**
 * Authenticates the WebSocket upgrade using a JWT supplied as the {@code token} query parameter.
 *
 * <p>Browsers cannot set Authorization headers on a WebSocket upgrade, so the access token rides as
 * a query parameter. On success the resolved {@link UserPrincipal} is stored in the handshake
 * attributes under {@code principal} for later per-subscription authorization. A valid,
 * non-blacklisted token is not enough on its own: the account is re-checked against the database on
 * every handshake so a banned, suspended, deactivated, or deleted account cannot open a connection
 * merely because its previously-issued token has not expired.
 */
@Component
@ConditionalOnProperty(prefix = "app.message.live", name = "enabled", havingValue = "true")
public class MessageWebSocketJwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String PRINCIPAL_ATTRIBUTE = "principal";

    private static final Logger log =
            LoggerFactory.getLogger(MessageWebSocketJwtHandshakeInterceptor.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final MessageUserRepository userRepository;

    public MessageWebSocketJwtHandshakeInterceptor(
            JwtTokenProvider jwtTokenProvider,
            TokenBlacklistService tokenBlacklistService,
            MessageUserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenBlacklistService = tokenBlacklistService;
        this.userRepository = userRepository;
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
            Optional<User> user =
                    userRepository.findByIdAndDeletedAtIsNullAndStatus(
                            claims.userId(), UserStatus.ACTIVE);
            if (user.isEmpty()) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            // Email is intentionally absent from the access token (PII); the WebSocket principal
            // only needs the user id and role for fan-out and authorization.
            attributes.put(
                    PRINCIPAL_ATTRIBUTE,
                    new UserPrincipal(
                            claims.userId(), null, claims.role(), user.get().getStatus().name()));
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
