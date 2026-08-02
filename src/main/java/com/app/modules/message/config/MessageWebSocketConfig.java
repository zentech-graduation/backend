package com.app.modules.message.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.app.common.security.config.CorsProperties;
import com.app.modules.message.live.MessageWebSocketAuthInterceptor;
import com.app.modules.message.live.MessageWebSocketJwtHandshakeInterceptor;

/**
 * STOMP/SockJS WebSocket configuration for real-time message delivery.
 *
 * <p>The handshake interceptor authenticates the connection via a JWT query parameter; the channel
 * interceptor authorizes each SUBSCRIBE against active conversation membership. Active only when
 * {@code app.message.live.enabled} is true.
 */
@Configuration
@EnableWebSocketMessageBroker
@ConditionalOnProperty(prefix = "app.message.live", name = "enabled", havingValue = "true")
public class MessageWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final MessageWebSocketJwtHandshakeInterceptor handshakeInterceptor;
    private final MessageWebSocketAuthInterceptor authInterceptor;
    private final CorsProperties corsProperties;

    public MessageWebSocketConfig(
            MessageWebSocketJwtHandshakeInterceptor handshakeInterceptor,
            MessageWebSocketAuthInterceptor authInterceptor,
            CorsProperties corsProperties) {
        this.handshakeInterceptor = handshakeInterceptor;
        this.authInterceptor = authInterceptor;
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/messages")
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

    private String[] allowedOrigins() {
        String origins = corsProperties.allowedOrigins();
        if (origins == null || origins.isBlank()) {
            return new String[] {"http://localhost:*"};
        }
        return origins.split("\\s*,\\s*");
    }
}
