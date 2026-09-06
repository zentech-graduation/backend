package com.app.modules.comment.live;

import java.util.UUID;

/**
 * Tracks which sessions are actively watching a post's comments, with TTL-based expiry in Redis.
 */
public interface CommentPresenceService {

    void registerWatcher(UUID postId, String sessionId);

    void unregisterWatcher(UUID postId, String sessionId);

    void refreshHeartbeat(UUID postId);

    long watcherCount(UUID postId);
}
