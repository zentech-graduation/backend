package com.app.modules.comment.live;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

/**
 * STOMP application handlers for watching a post's live comment stream.
 *
 * <p>Watching registers the session for presence and metrics. There is no catch-up broadcast: the
 * client fetches the first page over the already block-filtered REST comment list on {@code watch},
 * since a per-post catch-up cache has no viewer to filter blocked authors against. Live events
 * after that point are delivered by the fanout consumer, not from here.
 */
@Controller
@ConditionalOnProperty(prefix = "app.comment.live", name = "enabled", havingValue = "true")
public class CommentStompHandlers {

    private final CommentWebSocketSessionRegistry sessionRegistry;
    private final CommentPresenceService presenceService;

    public CommentStompHandlers(
            CommentWebSocketSessionRegistry sessionRegistry,
            CommentPresenceService presenceService) {
        this.sessionRegistry = sessionRegistry;
        this.presenceService = presenceService;
    }

    @MessageMapping("/watch/{postId}")
    public void watch(@DestinationVariable UUID postId, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        sessionRegistry.register(postId, sessionId);
        presenceService.registerWatcher(postId, sessionId);
    }

    @MessageMapping("/unwatch/{postId}")
    public void unwatch(@DestinationVariable UUID postId, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        sessionRegistry.unregister(postId, sessionId);
        presenceService.unregisterWatcher(postId, sessionId);
    }

    @MessageMapping("/heartbeat/{postId}")
    public void heartbeat(@DestinationVariable UUID postId) {
        presenceService.refreshHeartbeat(postId);
    }
}
