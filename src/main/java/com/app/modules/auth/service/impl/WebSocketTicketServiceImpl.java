package com.app.modules.auth.service.impl;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.auth.service.WebSocketTicketService;

/**
 * Redis-backed implementation of {@link WebSocketTicketService}.
 *
 * <p>Tickets live under {@code auth:ws-ticket:{ticket}} with a 30-second TTL, long enough for a
 * handshake that follows immediately after issue and short enough that a ticket captured from a log
 * or a referrer is already inert. Consumption is atomic via a Lua GET-then-DEL script so two
 * concurrent handshakes cannot both redeem one ticket.
 *
 * <p>Deliberately mirrors {@code OAuth2ExchangeCodeServiceImpl}: same entropy, same key shape, same
 * atomic single-use redemption. The two solve the same problem and should not drift.
 *
 * <p>The stored value is a live access token, which is a smaller exposure than the status quo it
 * replaces: Redis is not access-logged, the retention is 30 seconds rather than a log retention
 * window, and Redis already holds the token blacklist.
 */
@Service
public class WebSocketTicketServiceImpl implements WebSocketTicketService {

    private static final String KEY_PREFIX = "auth:ws-ticket:";
    private static final Duration TICKET_TTL = Duration.ofSeconds(30);

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

    public WebSocketTicketServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.consumeScript = new DefaultRedisScript<>(CONSUME_SCRIPT, String.class);
    }

    @Override
    public String issueTicket(String accessToken) {
        byte[] buf = new byte[32];
        SECURE_RANDOM.nextBytes(buf);
        String ticket = HexFormat.of().formatHex(buf);
        redisTemplate.opsForValue().set(KEY_PREFIX + ticket, accessToken, TICKET_TTL);
        return ticket;
    }

    @Override
    public String consumeTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            throw new AppException(ApiErrorCode.AUTH_TOKEN_INVALID);
        }
        String accessToken = redisTemplate.execute(consumeScript, List.of(KEY_PREFIX + ticket));
        if (accessToken == null) {
            throw new AppException(ApiErrorCode.AUTH_TOKEN_INVALID);
        }
        return accessToken;
    }
}
