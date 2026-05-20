package com.app.common.security.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import com.app.common.security.service.RateLimiterService;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis-backed true sliding-window rate limiter. Each request is recorded as a scored member in a
 * sorted set keyed by its arrival timestamp (ms). A Lua script atomically adds the new entry,
 * removes all entries older than {@code windowSeconds}, counts the survivors, and refreshes the
 * key's TTL — all in one round trip.
 *
 * <p>Unlike a fixed-window counter, this prevents the double-burst attack where an attacker sends
 * {@code maxAttempts} requests at the end of one window and another {@code maxAttempts} at the
 * start of the next. Any window of {@code windowSeconds} length contains at most {@code
 * maxAttempts} admitted requests.
 *
 * <p>On Redis failure, all requests are denied (fail-closed) to prevent brute-force attacks during
 * outages.
 */
@Slf4j
@Service
public class RateLimiterServiceImpl implements RateLimiterService {

    private static final String KEY_PREFIX = "auth:ratelimit:";

    // Lua sliding window: record this request as a timestamped member, evict members outside the
    // window, count survivors. A single atomic execution prevents race conditions between ZADD and
    // ZREMRANGEBYSCORE. ARGV[1]=now_ms, ARGV[2]=window_ms, ARGV[3]=unique_member.
    private static final String RATE_LIMIT_SCRIPT =
            "local now = tonumber(ARGV[1]) "
                    + "local window_ms = tonumber(ARGV[2]) "
                    + "redis.call('ZADD', KEYS[1], now, ARGV[3]) "
                    + "redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, now - window_ms) "
                    + "local count = redis.call('ZCARD', KEYS[1]) "
                    + "redis.call('EXPIRE', KEYS[1], math.ceil(window_ms / 1000) + 1) "
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
        long nowMs = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;
        String member = UUID.randomUUID().toString();
        try {
            Long count =
                    redisTemplate.execute(
                            rateLimitScript,
                            List.of(redisKey),
                            String.valueOf(nowMs),
                            String.valueOf(windowMs),
                            member);
            return count != null && count <= maxAttempts;
        } catch (DataAccessException e) {
            log.error("Rate limiter Redis failure for key {}; failing closed", redisKey, e);
            return false;
        }
    }
}
