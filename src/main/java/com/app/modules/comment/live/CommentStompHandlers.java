package com.app.modules.comment.live;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.app.modules.comment.service.CommentCacheService;

/**
 * STOMP application handlers for watching a post's live comment stream.
 *
 * <p>Watching registers the session for presence and metrics and replays the recent-comments cache
 * as a catch-up. Live events are delivered by the fanout consumer, not from here.
 */
@Controller
@ConditionalOnProperty(prefix = "app.comment.live", name = "enabled", havingValue = "true")
public class CommentStompHandlers {

    private final CommentWebSocketSessionRegistry sessionRegistry;
    private final CommentPresenceService presenceService;
    private final CommentCacheService cacheService;
    private final SimpMessagingTemplate messagingTemplate;

    public CommentStompHandlers(
            CommentWebSocketSessionRegistry sessionRegistry,
            CommentPresenceService presenceService,
            CommentCacheService cacheService,
            SimpMessagingTemplate messagingTemplate) {
        this.sessionRegistry = sessionRegistry;
        this.presenceService = presenceService;
        this.cacheService = cacheService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/watch/{postId}")
    public void watch(@DestinationVariable UUID postId, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        sessionRegistry.register(postId, sessionId);
        presenceService.registerWatcher(postId, sessionId);
        messagingTemplate.convertAndSend(
                "/topic/comments." + postId + ".catchup", cacheService.getOrRebuild(postId));
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
