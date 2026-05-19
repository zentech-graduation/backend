package com.app.common.security.impl;

import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import com.app.common.security.RateLimiterService;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis-backed sliding-window rate limiter. Uses a Lua script so {@code INCR} and {@code EXPIRE}
 * execute in a single atomic step and a crash between the two cannot leave a key without a TTL.
 *
 * <p>On Redis failure, all requests are denied (fail-closed) to prevent brute-force attacks during
 * outages.
 */
@Slf4j
@Service
public class RateLimiterServiceImpl implements RateLimiterService {

    private static final String KEY_PREFIX = "auth:ratelimit:";

    // Lua: increment the counter; on the first hit, attach the window TTL. Returns the post-incr
    // value so the caller can decide whether to admit the request.
    private static final String RATE_LIMIT_SCRIPT =
            "local count = redis.call('INCR', KEYS[1]) "
                    + "if count == 1 then "
                    + "  redis.call('EXPIRE', KEYS[1], ARGV[1]) "
                    + "end "
                    + "return count";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> rateLimitScript;

    public RateLimiterServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = new DefaultRedisScript<>(RATE_LIMIT_SCRIPT, Long.class);
    }

    @Override
    public boolean isAllowed(String key, int maxAttempts, long windowSeconds) {
        String redisKey = KEY_PREFIX + key;
        try {
            Long count =
                    redisTemplate.execute(
                            rateLimitScript, List.of(redisKey), String.valueOf(windowSeconds));
            return count != null && count <= maxAttempts;
        } catch (DataAccessException e) {
            log.error("Rate limiter Redis failure for key {}; failing closed", redisKey, e);
            return false;
        }
    }
}
