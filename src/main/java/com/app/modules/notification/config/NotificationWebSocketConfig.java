package com.app.modules.notification.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.app.common.security.config.CorsProperties;
import com.app.common.security.websocket.JwtHandshakeInterceptor;

/**
 * Registers the {@code /ws/notifications} STOMP endpoint. Active only when {@code
 * app.notification.live.enabled} is true.
 *
 * <p>Broker-wide concerns are shared across every STOMP endpoint and live in {@link
 * com.app.common.config.websocket.WebSocketBrokerConfig} instead of here; see that class for why.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.notification.live", name = "enabled", havingValue = "true")
public class NotificationWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor handshakeInterceptor;
    private final CorsProperties corsProperties;

    public NotificationWebSocketConfig(
            JwtHandshakeInterceptor handshakeInterceptor, CorsProperties corsProperties) {
        this.handshakeInterceptor = handshakeInterceptor;
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/notifications")
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
