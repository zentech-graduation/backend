package com.app.modules.post.live;

import java.util.Set;
import java.util.UUID;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import com.app.common.security.websocket.WebSocketSessionRegistry;

/**
 * Drops an outbound post live-fan-out frame per recipient session when that session's viewer is in
 * a block relationship with the post's owner.
 *
 * <p>Subscribe-time authorisation alone is not sufficient. A viewer who subscribed before being
 * blocked keeps that subscription, and without this filter would keep receiving like counts for a
 * post that now returns not-found to them over REST. That is a stealth block violation delivered
 * over a channel the block never reached, so the filter is required even though the event payload
 * names no actor.
 *
 * <p>{@link PostLiveFanoutConsumer} computes the owner's full block-counterparty set once per event
 * (one query, independent of subscriber count) and attaches it as the {@link
 * #BLOCKED_COUNTERPARTIES_HEADER} message header. The simple broker fans a single {@code
 * convertAndSend} out into one outbound message per subscribed session, invoking {@link #preSend}
 * once per session; returning {@code null} drops the frame for that session only.
 */
@Component
public class PostLiveBlockFilterInterceptor implements ChannelInterceptor {

    /**
     * Message header carrying the post owner's block-counterparty ids, as a {@code Set<UUID>}.
     * Absent (not merely empty) when the destination is not a block-filtered post broadcast, in
     * which case this interceptor is inert.
     */
    public static final String BLOCKED_COUNTERPARTIES_HEADER = "postBlockedCounterparties";

    private final WebSocketSessionRegistry sessionRegistry;

    public PostLiveBlockFilterInterceptor(WebSocketSessionRegistry sessionRegistry) {
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
