package com.app.common.security.websocket;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * Deny-by-default guard on every client SEND frame reaching the shared inbound channel.
 *
 * <p>{@code SimpMessagingTemplate.convertAndSend} - the mechanism every server-side fanout consumer
 * uses to publish a real event - writes directly to the broker's outbound channel and never
 * traverses {@code clientInboundChannel}, so nothing server-originated is affected by this guard. A
 * client SEND, by contrast, always enters through this channel; without this guard a client could
 * address a SEND frame directly at a broker-relayed {@code /topic/**} destination - another user's
 * notification topic, or any post's comment-events topic - and every subscriber would receive it
 * indistinguishably from a server-published event.
 *
 * <p>Permits only SEND frames whose destination begins with the configured application destination
 * prefix ({@link #APPLICATION_DESTINATION_PREFIX}), where {@code @MessageMapping} handlers and the
 * module-scoped interceptors ({@code CommentWebSocketAuthInterceptor}, {@code
 * NotificationWebSocketAuthInterceptor}) take over. Registered first in the interceptor chain
 * ({@link com.app.common.config.websocket.WebSocketBrokerConfig}) so a forged SEND is rejected
 * before it reaches any module-specific logic.
 *
 * <p>Deliberately does not extend either module-scoped interceptor: each of those owns only its own
 * module's destination prefix by design, and scoping this guard to one module would leave every
 * other {@code /topic/**} destination - including ones added by a future module - unguarded again.
 */
@Component
public class BrokerSendGuardInterceptor implements ChannelInterceptor {

    /** Must match {@code MessageBrokerRegistry.setApplicationDestinationPrefixes}. */
    public static final String APPLICATION_DESTINATION_PREFIX = "/app";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (!StompCommand.SEND.equals(accessor.getCommand())) {
            return message;
        }
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(APPLICATION_DESTINATION_PREFIX)) {
            throw new MessageDeliveryException(message, "Send not permitted");
        }
        return message;
    }
}
