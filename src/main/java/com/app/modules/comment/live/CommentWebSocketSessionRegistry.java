package com.app.modules.comment.live;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * In-memory map of post id to the local WebSocket session ids watching it.
 *
 * <p>Used for metrics and catch-up bookkeeping. Message delivery itself is handled by the STOMP
 * SimpleBroker per destination, not by this registry.
 */
@Component
@ConditionalOnProperty(prefix = "app.comment.live", name = "enabled", havingValue = "true")
public class CommentWebSocketSessionRegistry {

    private final ConcurrentHashMap<UUID, Set<String>> sessionsByPost = new ConcurrentHashMap<>();

    public void register(UUID postId, String sessionId) {
        sessionsByPost.computeIfAbsent(postId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    public void unregister(UUID postId, String sessionId) {
        sessionsByPost.computeIfPresent(
                postId,
                (k, sessions) -> {
                    sessions.remove(sessionId);
                    return sessions.isEmpty() ? null : sessions;
                });
    }

    public Set<String> sessionsForPost(UUID postId) {
        return sessionsByPost.getOrDefault(postId, Set.of());
    }

    public int totalSessions() {
        return sessionsByPost.values().stream().mapToInt(Set::size).sum();
    }
}
