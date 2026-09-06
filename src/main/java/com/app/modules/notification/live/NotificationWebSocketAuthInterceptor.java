package com.app.modules.notification.live;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import com.app.common.security.user.UserPrincipal;
import com.app.common.security.websocket.JwtHandshakeInterceptor;

/**
 * Enforces deny-by-default subscription authorization on every {@code /topic/notifications.*}
 * SUBSCRIBE: the destination must name exactly the handshake principal's own user id.
 *
 * <p>Unlike the comment topic (shared by every viewer of a post), a notification topic is a
 * per-user address, so there is no domain query to run - the check is a string comparison against
 * the handshake principal. A malformed id, a missing principal, or a destination with any trailing
 * segment is rejected the same as a subscription to another user's topic; only an exact match to
 * the caller's own id passes. Registered on the shared inbound channel, so this guards a session
 * regardless of which STOMP endpoint it connected through.
 */
@Component
public class NotificationWebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationWebSocketAuthInterceptor.class);

    private static final String PREFIX = "/topic/notifications.";
    private static final Pattern DESTINATION =
            Pattern.compile("/topic/notifications\\.([0-9a-fA-F\\-]{36})");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (!StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(PREFIX)) {
            return message;
        }
        UUID viewerId = resolveViewer(accessor);
        Matcher matcher = DESTINATION.matcher(destination);
        if (matcher.matches()
                && viewerId != null
                && matcher.group(1).equalsIgnoreCase(viewerId.toString())) {
            return message;
        }
        log.warn(
                "Notification topic subscription rejected: userId={} destination={} sessionId={}",
                viewerId,
                destination,
                accessor.getSessionId());
        throw new MessageDeliveryException(message, "Subscription not permitted");
    }

    private UUID resolveViewer(StompHeaderAccessor accessor) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        Object principal =
                attributes == null
                        ? null
                        : attributes.get(JwtHandshakeInterceptor.PRINCIPAL_ATTRIBUTE);
        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal.userId();
        }
        return null;
    }
}
