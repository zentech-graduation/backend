package com.app.modules.comment.live;

import java.time.Duration;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.comment.live", name = "enabled", havingValue = "true")
public class CommentPresenceServiceImpl implements CommentPresenceService {

    private static final Duration TTL = Duration.ofSeconds(300);
    private static final String KEY_PREFIX = "comment:watchers:";

    private final StringRedisTemplate redisTemplate;

    public CommentPresenceServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void registerWatcher(UUID postId, String sessionId) {
        String key = key(postId);
        redisTemplate.opsForSet().add(key, sessionId);
        redisTemplate.expire(key, TTL);
    }

    @Override
    public void unregisterWatcher(UUID postId, String sessionId) {
        redisTemplate.opsForSet().remove(key(postId), sessionId);
    }

    @Override
    public void refreshHeartbeat(UUID postId) {
        redisTemplate.expire(key(postId), TTL);
    }

    @Override
    public long watcherCount(UUID postId) {
        Long count = redisTemplate.opsForSet().size(key(postId));
        return count == null ? 0 : count;
    }

    private static String key(UUID postId) {
        return KEY_PREFIX + postId;
    }
}
