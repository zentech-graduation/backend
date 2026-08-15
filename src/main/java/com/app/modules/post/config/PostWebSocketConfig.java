package com.app.modules.post.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.app.common.security.config.CorsProperties;
import com.app.common.security.websocket.JwtHandshakeInterceptor;

/**
 * Registers the {@code /ws/posts} STOMP endpoint. Active only when {@code app.post.live.enabled} is
 * true.
 *
 * <p>Broker-wide concerns are shared across every STOMP endpoint and live in {@link
 * com.app.common.config.websocket.WebSocketBrokerConfig} instead of here; see that class for why.
 *
 * <p>This endpoint exists so the post live tier is usable on its own when the comment tier is
 * disabled. It does not force a second connection on a client that wants both streams: STOMP
 * destinations are broker-wide rather than endpoint-scoped, so a client already connected to {@code
 * /ws/comments} may subscribe to {@code /topic/posts.{postId}.events} over that same connection,
 * subject to {@link com.app.modules.post.live.PostWebSocketAuthInterceptor} authorising it.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.post.live", name = "enabled", havingValue = "true")
public class PostWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor handshakeInterceptor;
    private final CorsProperties corsProperties;

    public PostWebSocketConfig(
            JwtHandshakeInterceptor handshakeInterceptor, CorsProperties corsProperties) {
        this.handshakeInterceptor = handshakeInterceptor;
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/posts")
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
