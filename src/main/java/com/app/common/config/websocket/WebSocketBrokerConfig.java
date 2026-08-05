package com.app.common.config.websocket;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import com.app.common.security.websocket.BrokerSendGuardInterceptor;
import com.app.common.security.websocket.SessionTrackingWebSocketHandlerDecoratorFactory;
import com.app.modules.comment.live.CommentLiveBlockFilterInterceptor;
import com.app.modules.comment.live.CommentWebSocketAuthInterceptor;
import com.app.modules.notification.live.NotificationWebSocketAuthInterceptor;

/**
 * Owns the broker-wide STOMP concerns shared by every WebSocket endpoint in the application: the
 * message broker itself, the one inbound channel's interceptors, and the session-tracking transport
 * decorator that backs revocation. Active when either {@code app.comment.live.enabled} or {@code
 * app.notification.live.enabled} is true.
 *
 * <p>There is exactly one {@code clientInboundChannel} and one simple broker regardless of how many
 * {@code registerStompEndpoints} calls contribute endpoints to it -
 * {@code @EnableWebSocketMessageBroker} must therefore be declared exactly once, and every {@link
 * WebSocketMessageBrokerConfigurer} bean's overrides are aggregated onto that single broker.
 * Keeping this declaration on a per-endpoint config (as it previously was on the comment module's
 * config) meant enabling the notification endpoint alone, with the comment endpoint left off,
 * produced no STOMP infrastructure at all: a silently non-existent endpoint. Centralizing it here,
 * gated on either flag, removes that trap.
 */
@Configuration
@EnableWebSocketMessageBroker
@ConditionalOnExpression(
        "${app.comment.live.enabled:false} or ${app.notification.live.enabled:false}")
public class WebSocketBrokerConfig implements WebSocketMessageBrokerConfigurer {

    private final BrokerSendGuardInterceptor brokerSendGuardInterceptor;
    private final CommentWebSocketAuthInterceptor commentAuthInterceptor;
    private final NotificationWebSocketAuthInterceptor notificationAuthInterceptor;
    private final SessionTrackingWebSocketHandlerDecoratorFactory sessionTrackingDecoratorFactory;
    private final CommentLiveBlockFilterInterceptor commentLiveBlockFilterInterceptor;

    public WebSocketBrokerConfig(
            BrokerSendGuardInterceptor brokerSendGuardInterceptor,
            CommentWebSocketAuthInterceptor commentAuthInterceptor,
            NotificationWebSocketAuthInterceptor notificationAuthInterceptor,
            SessionTrackingWebSocketHandlerDecoratorFactory sessionTrackingDecoratorFactory,
            CommentLiveBlockFilterInterceptor commentLiveBlockFilterInterceptor) {
        this.brokerSendGuardInterceptor = brokerSendGuardInterceptor;
        this.commentAuthInterceptor = commentAuthInterceptor;
        this.notificationAuthInterceptor = notificationAuthInterceptor;
        this.sessionTrackingDecoratorFactory = sessionTrackingDecoratorFactory;
        this.commentLiveBlockFilterInterceptor = commentLiveBlockFilterInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes(
                BrokerSendGuardInterceptor.APPLICATION_DESTINATION_PREFIX);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // brokerSendGuardInterceptor runs first so a forged SEND at a /topic/** destination is
        // rejected before any module-specific interceptor logic runs.
        registration.interceptors(
                brokerSendGuardInterceptor, commentAuthInterceptor, notificationAuthInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(commentLiveBlockFilterInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(sessionTrackingDecoratorFactory);
    }
}
