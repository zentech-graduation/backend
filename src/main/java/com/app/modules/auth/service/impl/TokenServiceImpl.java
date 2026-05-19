package com.app.modules.auth.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import com.app.modules.auth.exception.TokenNotFoundException;
import com.app.modules.auth.service.TokenService;

/**
 * Redis-backed single-use token store. Each token is keyed by SHA-256 of the raw value and the
 * stored value is the owning user id. A reverse index ({@code <prefix>user:<userId>}) lets a new
 * issue invalidate any prior pending token for the same user. Consumption is atomic via Lua so a
 * concurrent verify/reset cannot run twice.
 */
@Service
public class TokenServiceImpl implements TokenService {

    private static final String EMAIL_VERIFICATION_PREFIX = "auth:token:email-verification:";
    private static final String PASSWORD_RESET_PREFIX = "auth:token:password-reset:";
    private static final String USER_INDEX_INFIX = "user:";
    private static final Duration EMAIL_VERIFICATION_TTL = Duration.ofHours(24);
    private static final Duration PASSWORD_RESET_TTL = Duration.ofMinutes(15);
    private static final String SHA_256 = "SHA-256";

    // 256-bit entropy; thread-safe — a single static instance is correct for SecureRandom.
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // Atomic consume: GET then DEL. Returns the stored userId or nil when absent.
    private static final String CONSUME_SCRIPT =
            "local val = redis.call('GET', KEYS[1]) "
                    + "if val then "
                    + "  redis.call('DEL', KEYS[1]) "
                    + "  return val "
                    + "else "
                    + "  return nil "
                    + "end";

    // Atomic create: delete any prior token for the user, then set new primary + reverse keys.
    // KEYS[1] = reverseKey (e.g. "auth:token:email-verification:user:<userId>")
    // ARGV[1] = new tokenHash
    // ARGV[2] = userId (value stored in the primary key)
    // ARGV[3] = prefix (prepended to tokenHash to form the primary key)
    // ARGV[4] = ttlSeconds (cast to number inside the script)
    private static final String CREATE_SCRIPT =
            "local prev = redis.call('GET', KEYS[1]) "
                    + "if prev then "
                    + "  redis.call('DEL', ARGV[3] .. prev) "
                    + "end "
                    + "redis.call('SET', ARGV[3] .. ARGV[1], ARGV[2], 'EX', tonumber(ARGV[4])) "
                    + "redis.call('SET', KEYS[1], ARGV[1], 'EX', tonumber(ARGV[4])) "
                    + "return ARGV[1]";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<String> consumeScript;
    private final RedisScript<String> createScript;

    public TokenServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.consumeScript = new DefaultRedisScript<>(CONSUME_SCRIPT, String.class);
        this.createScript = new DefaultRedisScript<>(CREATE_SCRIPT, String.class);
    }

    @Override
    public String createEmailVerificationToken(UUID userId) {
        return createToken(userId, EMAIL_VERIFICATION_PREFIX, EMAIL_VERIFICATION_TTL);
    }

    @Override
    public UUID consumeEmailVerificationToken(String rawToken) {
        return consumeToken(rawToken, EMAIL_VERIFICATION_PREFIX, "Email verification token");
    }

    @Override
    public String createPasswordResetToken(UUID userId) {
        return createToken(userId, PASSWORD_RESET_PREFIX, PASSWORD_RESET_TTL);
    }

    @Override
    public UUID consumePasswordResetToken(String rawToken) {
        return consumeToken(rawToken, PASSWORD_RESET_PREFIX, "Password reset token");
    }

    private String createToken(UUID userId, String prefix, Duration ttl) {
        byte[] buf = new byte[32];
        SECURE_RANDOM.nextBytes(buf);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);

        String tokenHash = sha256(rawToken);
        String reverseKey = prefix + USER_INDEX_INFIX + userId;
        long ttlSeconds = ttl.toSeconds();

        redisTemplate.execute(
                createScript,
                List.of(reverseKey),
                tokenHash,
                userId.toString(),
                prefix,
                String.valueOf(ttlSeconds));

        return rawToken;
    }

    private UUID consumeToken(String rawToken, String prefix, String label) {
        String tokenKey = prefix + sha256(rawToken);
        String userIdValue = redisTemplate.execute(consumeScript, List.of(tokenKey));
        if (userIdValue == null) {
            throw new TokenNotFoundException(label + " is invalid or has expired");
        }

        UUID userId = UUID.fromString(userIdValue);
        // Best-effort cleanup of the reverse index so it does not pin a stale hash for the
        // remainder of the TTL window. A failure here cannot reintroduce token reuse since
        // the primary key has already been deleted atomically above.
        redisTemplate.delete(prefix + USER_INDEX_INFIX + userId);
        return userId;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable in JVM", e);
        }
    }
}
