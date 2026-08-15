package com.app.modules.comment.live;

import java.util.Set;
import java.util.UUID;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import com.app.common.security.websocket.WebSocketSessionRegistry;

/**
 * Drops an outbound comment live-fan-out frame per recipient session when that session's viewer is
 * in a block relationship with the event's content owner.
 *
 * <p>{@link CommentLiveFanoutConsumer} computes the owner's full block-counterparty set once per
 * event (one query, independent of subscriber count) and attaches it as the {@link
 * #BLOCKED_COUNTERPARTIES_HEADER} message header. The simple broker fans a single {@code
 * convertAndSend} out into one outbound message per subscribed session, invoking this interceptor's
 * {@link #preSend} once per session; returning {@code null} drops the frame for that session only;
 * other subscribers are unaffected.
 *
 * <p>The per-session check itself is an in-memory {@link Set#contains}, resolving the frame's
 * target session to a viewer id via {@link WebSocketSessionRegistry}: no additional database access
 * per subscriber, and no cache that could outlive a block created mid-session.
 */
@Component
public class CommentLiveBlockFilterInterceptor implements ChannelInterceptor {

    /**
     * Message header carrying the fanned-out event's content owner's block-counterparty ids, as a
     * {@code Set<UUID>}. Absent (not merely empty) when the destination is not a block-filtered
     * comment broadcast, in which case this interceptor is inert.
     */
    public static final String BLOCKED_COUNTERPARTIES_HEADER = "commentBlockedCounterparties";

    private final WebSocketSessionRegistry sessionRegistry;

    public CommentLiveBlockFilterInterceptor(WebSocketSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        Object header = message.getHeaders().get(BLOCKED_COUNTERPARTIES_HEADER);
        if (!(header instanceof Set<?> blockedCounterparties) || blockedCounterparties.isEmpty()) {
            return message;
        }
        String sessionId = SimpMessageHeaderAccessor.wrap(message).getSessionId();
        if (sessionId == null) {
            return message;
        }
        UUID viewerId = sessionRegistry.findViewerId(sessionId);
        if (viewerId != null && blockedCounterparties.contains(viewerId)) {
            return null;
        }
        return message;
    }
}
