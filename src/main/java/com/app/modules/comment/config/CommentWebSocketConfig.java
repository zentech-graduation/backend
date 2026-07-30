package com.app.modules.comment.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import com.app.common.security.config.CorsProperties;
import com.app.common.security.websocket.JwtHandshakeInterceptor;
import com.app.common.security.websocket.SessionTrackingWebSocketHandlerDecoratorFactory;
import com.app.modules.comment.live.CommentWebSocketAuthInterceptor;

/**
 * STOMP/SockJS WebSocket configuration for real-time comment delivery.
 *
 * <p>The handshake interceptor authenticates the connection via a JWT query parameter; the channel
 * interceptor authorizes each SUBSCRIBE against post visibility. Active only when {@code
 * app.comment.live.enabled} is true.
 */
@Configuration
@EnableWebSocketMessageBroker
@ConditionalOnProperty(prefix = "app.comment.live", name = "enabled", havingValue = "true")
public class CommentWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor handshakeInterceptor;
    private final CommentWebSocketAuthInterceptor authInterceptor;
    private final CorsProperties corsProperties;
    private final SessionTrackingWebSocketHandlerDecoratorFactory sessionTrackingDecoratorFactory;

    public CommentWebSocketConfig(
            JwtHandshakeInterceptor handshakeInterceptor,
            CommentWebSocketAuthInterceptor authInterceptor,
            CorsProperties corsProperties,
            SessionTrackingWebSocketHandlerDecoratorFactory sessionTrackingDecoratorFactory) {
        this.handshakeInterceptor = handshakeInterceptor;
        this.authInterceptor = authInterceptor;
        this.corsProperties = corsProperties;
        this.sessionTrackingDecoratorFactory = sessionTrackingDecoratorFactory;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/comments")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins())
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(sessionTrackingDecoratorFactory);
    }

    private String[] allowedOrigins() {
        String origins = corsProperties.allowedOrigins();
        if (origins == null || origins.isBlank()) {
            return new String[] {"http://localhost:*"};
        }
        return origins.split("\\s*,\\s*");
    }
}
