package com.app.modules.comment.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.app.common.security.config.CorsProperties;
import com.app.common.security.websocket.JwtHandshakeInterceptor;

/**
 * Registers the {@code /ws/comments} STOMP endpoint. Active only when {@code
 * app.comment.live.enabled} is true.
 *
 * <p>Broker-wide concerns (message broker, inbound channel interceptors, transport decorators) are
 * shared across every STOMP endpoint and live in {@link
 * com.app.common.config.websocket.WebSocketBrokerConfig} instead of here, since Spring aggregates
 * every {@link WebSocketMessageBrokerConfigurer} bean's callbacks onto the one broker and one
 * inbound channel the application has.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.comment.live", name = "enabled", havingValue = "true")
public class CommentWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor handshakeInterceptor;
    private final CorsProperties corsProperties;

    public CommentWebSocketConfig(
            JwtHandshakeInterceptor handshakeInterceptor, CorsProperties corsProperties) {
        this.handshakeInterceptor = handshakeInterceptor;
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/comments")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins())
                .withSockJS();
    }

    // Deny-by-default: a blank CORS_ALLOWED_ORIGINS yields an empty array here, matching the
    // REST surface's fail-closed behaviour, rather than quietly admitting localhost.
    // Package-private rather than private so the mapping can be unit tested directly, without
    // mocking the StompEndpointRegistry fluent builder chain.
    String[] allowedOrigins() {
        return corsProperties.allowedOriginList().toArray(String[]::new);
    }
}
