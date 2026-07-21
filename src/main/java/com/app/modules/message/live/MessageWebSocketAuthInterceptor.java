package com.app.modules.message.live;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import com.app.common.security.user.UserPrincipal;
import com.app.modules.message.repository.ConversationParticipantRepository;

/**
 * Enforces active-participant membership on every STOMP SUBSCRIBE to a conversation topic.
 *
 * <p>The destination encodes the conversation id ({@code
 * /topic/conversations.{conversationId}.messages}); the subscription is rejected unless the
 * handshake principal is an active (not left) participant of that conversation. This is the sole
 * authorization boundary for live message delivery - a rejected check must throw, never silently
 * pass the frame through.
 */
@Component
@ConditionalOnProperty(prefix = "app.message.live", name = "enabled", havingValue = "true")
public class MessageWebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Pattern DESTINATION =
            Pattern.compile("/topic/conversations\\.([0-9a-fA-F\\-]{36})\\.messages");

    private final ConversationParticipantRepository participantRepository;

    public MessageWebSocketAuthInterceptor(
            ConversationParticipantRepository participantRepository) {
        this.participantRepository = participantRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (!StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }
        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }
        Matcher matcher = DESTINATION.matcher(destination);
        if (!matcher.matches()) {
            return message;
        }
        UUID conversationId = UUID.fromString(matcher.group(1));
        UUID viewerId = resolveViewer(accessor);
        if (viewerId == null
                || !participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNull(
                        conversationId, viewerId)) {
            throw new MessageDeliveryException(message, "Subscription not permitted");
        }
        return message;
    }

    private UUID resolveViewer(StompHeaderAccessor accessor) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        Object principal =
                attributes == null
                        ? null
                        : attributes.get(
                                MessageWebSocketJwtHandshakeInterceptor.PRINCIPAL_ATTRIBUTE);
        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal.userId();
        }
        return null;
    }
}
