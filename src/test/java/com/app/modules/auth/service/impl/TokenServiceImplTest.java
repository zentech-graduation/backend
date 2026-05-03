package com.app.modules.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import com.app.modules.auth.exception.TokenNotFoundException;

@ExtendWith(MockitoExtension.class)
class TokenServiceImplTest {

    private static final String EMAIL_PREFIX = "auth:token:email-verification:";
    private static final String RESET_PREFIX = "auth:token:password-reset:";
    private static final String USER_INFIX = "user:";

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private TokenServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new TokenServiceImpl(redisTemplate);
    }

    @Test
    void createEmailVerificationToken_storesHashedTokenAndReverseIndexWith24HourTtl() {
        UUID userId = UUID.randomUUID();
        when(valueOps.get(EMAIL_PREFIX + USER_INFIX + userId)).thenReturn(null);

        String raw = service.createEmailVerificationToken(userId);

        verify(valueOps)
                .set(
                        eq(EMAIL_PREFIX + sha256(raw)),
                        eq(userId.toString()),
                        eq(Duration.ofHours(24)));
        verify(valueOps)
                .set(
                        eq(EMAIL_PREFIX + USER_INFIX + userId),
                        eq(sha256(raw)),
                        eq(Duration.ofHours(24)));
    }

    @Test
    void createEmailVerificationToken_invalidatesPriorPendingTokenForSameUser() {
        UUID userId = UUID.randomUUID();
        String oldHash = "old-hash";
        when(valueOps.get(EMAIL_PREFIX + USER_INFIX + userId)).thenReturn(oldHash);

        service.createEmailVerificationToken(userId);

        verify(redisTemplate).delete(EMAIL_PREFIX + oldHash);
    }

    @Test
    void createEmailVerificationToken_returnsUniqueRawTokens() {
        UUID userId = UUID.randomUUID();
        String first = service.createEmailVerificationToken(userId);
        String second = service.createEmailVerificationToken(userId);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void consumeEmailVerificationToken_returnsUserIdOnHappyPath() {
        UUID userId = UUID.randomUUID();
        String raw = "raw-verify-" + UUID.randomUUID();
        when(redisTemplate.execute(any(RedisScript.class), anyList()))
                .thenReturn(userId.toString());

        UUID result = service.consumeEmailVerificationToken(raw);

        assertThat(result).isEqualTo(userId);
        verify(redisTemplate).delete(EMAIL_PREFIX + USER_INFIX + userId);
    }

    @Test
    void consumeEmailVerificationToken_valid_executesLuaScript() {
        UUID userId = UUID.randomUUID();
        String raw = "raw-script";
        when(redisTemplate.execute(any(RedisScript.class), anyList()))
                .thenReturn(userId.toString());

        service.consumeEmailVerificationToken(raw);

        verify(redisTemplate, times(1))
                .execute(any(RedisScript.class), eq(java.util.List.of(EMAIL_PREFIX + sha256(raw))));
    }

    @Test
    void consumeEmailVerificationToken_throwsWhenAbsent() {
        when(redisTemplate.execute(any(RedisScript.class), anyList())).thenReturn(null);

        assertThatThrownBy(() -> service.consumeEmailVerificationToken("ghost"))
                .isInstanceOf(TokenNotFoundException.class);
        verify(redisTemplate, never()).delete(any(String.class));
    }

    @Test
    void createPasswordResetToken_storesHashedTokenAndReverseIndexWith15MinuteTtl() {
        UUID userId = UUID.randomUUID();
        when(valueOps.get(RESET_PREFIX + USER_INFIX + userId)).thenReturn(null);

        String raw = service.createPasswordResetToken(userId);

        verify(valueOps)
                .set(
                        eq(RESET_PREFIX + sha256(raw)),
                        eq(userId.toString()),
                        eq(Duration.ofMinutes(15)));
        verify(valueOps)
                .set(
                        eq(RESET_PREFIX + USER_INFIX + userId),
                        eq(sha256(raw)),
                        eq(Duration.ofMinutes(15)));
    }

    @Test
    void consumePasswordResetToken_returnsUserIdOnHappyPath() {
        UUID userId = UUID.randomUUID();
        when(redisTemplate.execute(any(RedisScript.class), anyList()))
                .thenReturn(userId.toString());

        UUID result = service.consumePasswordResetToken("raw-reset");

        assertThat(result).isEqualTo(userId);
        verify(redisTemplate).delete(RESET_PREFIX + USER_INFIX + userId);
    }

    @Test
    void consumePasswordResetToken_throwsWhenAbsent() {
        when(redisTemplate.execute(any(RedisScript.class), anyList())).thenReturn(null);

        assertThatThrownBy(() -> service.consumePasswordResetToken("nope"))
                .isInstanceOf(TokenNotFoundException.class);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
