package com.app.common.security.service.impl;

import java.time.Duration;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.security.service.TokenBlacklistService;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis-backed JWT blacklist. Each entry is keyed by the token's {@code jti} claim and carries a
 * TTL equal to the token's remaining lifetime so that Redis evicts the entry automatically once the
 * original token would have expired anyway.
 */
@Slf4j
@Service
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private static final String KEY_PREFIX = "auth:blacklist:";
    private static final String SENTINEL = "1";

    private final StringRedisTemplate redisTemplate;

    public TokenBlacklistServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void blacklist(String jti, long remainingTtlSeconds) {
        if (jti == null || jti.isBlank() || remainingTtlSeconds <= 0) {
            return;
        }
        try {
            redisTemplate
                    .opsForValue()
                    .set(KEY_PREFIX + jti, SENTINEL, Duration.ofSeconds(remainingTtlSeconds));
        } catch (DataAccessException e) {
            log.error(
                    "Failed to blacklist JWT jti={}; access token remains valid until expiry",
                    jti,
                    e);
            throw new AppException(ApiErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
    }
}
