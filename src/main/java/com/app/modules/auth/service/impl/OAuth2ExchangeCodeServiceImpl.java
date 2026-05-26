package com.app.modules.auth.service.impl;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.auth.service.OAuth2ExchangeCodeService;

/**
 * Redis-backed implementation of {@link OAuth2ExchangeCodeService}. Codes are stored under {@code
 * auth:oauth2:exchange:{code}} with a 120-second TTL. Consumption is atomic via a Lua GET-then-DEL
 * script so a concurrent redemption attempt cannot succeed twice.
 */
@Service
public class OAuth2ExchangeCodeServiceImpl implements OAuth2ExchangeCodeService {

    private static final String KEY_PREFIX = "auth:oauth2:exchange:";
    private static final Duration EXCHANGE_CODE_TTL = Duration.ofSeconds(120);

    // 256-bit entropy; a single static instance is correct for SecureRandom.
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // Atomic GET-then-DEL: returns the stored value or nil when the key is absent or expired.
    private static final String CONSUME_SCRIPT =
            "local val = redis.call('GET', KEYS[1]) "
                    + "if val then "
                    + "  redis.call('DEL', KEYS[1]) "
                    + "  return val "
                    + "else "
                    + "  return nil "
                    + "end";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<String> consumeScript;

    public OAuth2ExchangeCodeServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.consumeScript = new DefaultRedisScript<>(CONSUME_SCRIPT, String.class);
    }

    @Override
    public String storeExchangeCode(UUID userId) {
        byte[] buf = new byte[32];
        SECURE_RANDOM.nextBytes(buf);
        String code = HexFormat.of().formatHex(buf);
        redisTemplate.opsForValue().set(KEY_PREFIX + code, userId.toString(), EXCHANGE_CODE_TTL);
        return code;
    }

    @Override
    public UUID consumeExchangeCode(String code) {
        String userIdValue = redisTemplate.execute(consumeScript, List.of(KEY_PREFIX + code));
        if (userIdValue == null) {
            throw new AppException(ApiErrorCode.AUTH_OAUTH2_EXCHANGE_CODE_INVALID);
        }
        return UUID.fromString(userIdValue);
    }
}
