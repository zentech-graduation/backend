package com.app.modules.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
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
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    // -----------------------------------------------------------------------
    // FIX-10: createToken atomicity via Lua script
    // -----------------------------------------------------------------------

    @Test
    void createEmailVerificationToken_invokesLuaScriptWithCorrectKeysAndArgs() {
        UUID userId = UUID.randomUUID();
        String expectedReverseKey = EMAIL_PREFIX + USER_INFIX + userId;

        // The create Lua script returns the token hash; we return a dummy value here because
        // the actual return value (rawToken) is generated inside createToken before the call.
        when(redisTemplate.execute(
                        any(RedisScript.class),
                        anyList(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString()))
                .thenReturn("some-hash");

        String raw = service.createEmailVerificationToken(userId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> arg1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> arg2 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> arg3 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> arg4 = ArgumentCaptor.forClass(String.class);

        verify(redisTemplate)
                .execute(
                        any(RedisScript.class),
                        keysCaptor.capture(),
                        arg1.capture(),
                        arg2.capture(),
                        arg3.capture(),
                        arg4.capture());

        // KEYS[1] must be the reverse key
        assertThat(keysCaptor.getValue()).containsExactly(expectedReverseKey);
        // ARGV[1] = tokenHash = sha256(rawToken)
        assertThat(arg1.getValue()).isEqualTo(sha256(raw));
        // ARGV[2] = userId string
        assertThat(arg2.getValue()).isEqualTo(userId.toString());
        // ARGV[3] = prefix
        assertThat(arg3.getValue()).isEqualTo(EMAIL_PREFIX);
        // ARGV[4] = ttl in seconds for 24 hours
        assertThat(arg4.getValue()).isEqualTo(String.valueOf(Duration.ofHours(24).toSeconds()));
    }

    @Test
    void createPasswordResetToken_invokesLuaScriptWithCorrectKeysAndArgs() {
        UUID userId = UUID.randomUUID();
        String expectedReverseKey = RESET_PREFIX + USER_INFIX + userId;

        when(redisTemplate.execute(
                        any(RedisScript.class),
                        anyList(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString()))
                .thenReturn("some-hash");

        String raw = service.createPasswordResetToken(userId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> arg1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> arg2 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> arg3 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> arg4 = ArgumentCaptor.forClass(String.class);

        verify(redisTemplate)
                .execute(
                        any(RedisScript.class),
                        keysCaptor.capture(),
                        arg1.capture(),
                        arg2.capture(),
                        arg3.capture(),
                        arg4.capture());

        assertThat(keysCaptor.getValue()).containsExactly(expectedReverseKey);
        assertThat(arg1.getValue()).isEqualTo(sha256(raw));
        assertThat(arg2.getValue()).isEqualTo(userId.toString());
        assertThat(arg3.getValue()).isEqualTo(RESET_PREFIX);
        // ARGV[4] = ttl in seconds for 15 minutes
        assertThat(arg4.getValue()).isEqualTo(String.valueOf(Duration.ofMinutes(15).toSeconds()));
    }

    // Note: The Lua script itself handles the prior-token invalidation atomically inside Redis.
    // With a mocked RedisTemplate we cannot observe the internal DEL that the script performs;
    // we can only confirm the script is invoked — the integration test (AuthControllerIT) covers
    // end-to-end correctness of the invalidation logic via a real Redis container.
    @Test
    void createEmailVerificationToken_delegatesInvalidationToLuaScript() {
        UUID userId = UUID.randomUUID();
        // Any prior token invalidation is handled inside the Lua script; the Java layer must
        // not issue separate GET/DEL calls — verifying execute() is called exactly once is
        // sufficient to prove the atomic path is taken.
        when(redisTemplate.execute(
                        any(RedisScript.class),
                        anyList(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString()))
                .thenReturn("new-hash");

        service.createEmailVerificationToken(userId);

        verify(redisTemplate, times(1))
                .execute(
                        any(RedisScript.class),
                        anyList(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString());
        // Java layer must not touch valueOps for creation — all Redis work is in the script.
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
        verify(redisTemplate, never()).delete(anyString());
    }

    // -----------------------------------------------------------------------
    // FIX-15: SecureRandom token generation
    // -----------------------------------------------------------------------

    @Test
    void createEmailVerificationToken_rawTokenIs43CharactersLong() {
        UUID userId = UUID.randomUUID();
        when(redisTemplate.execute(
                        any(RedisScript.class),
                        anyList(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString()))
                .thenReturn("dummy");

        String raw = service.createEmailVerificationToken(userId);

        // Base64url of 32 bytes without padding is always exactly 43 characters.
        assertThat(raw).hasSize(43);
    }

    @Test
    void createEmailVerificationToken_consecutiveCallsProduceDifferentTokens() {
        UUID userId = UUID.randomUUID();
        when(redisTemplate.execute(
                        any(RedisScript.class),
                        anyList(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString()))
                .thenReturn("dummy");

        String first = service.createEmailVerificationToken(userId);
        String second = service.createEmailVerificationToken(userId);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void createEmailVerificationToken_rawTokenContainsOnlyUrlSafeBase64Characters() {
        UUID userId = UUID.randomUUID();
        when(redisTemplate.execute(
                        any(RedisScript.class),
                        anyList(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString()))
                .thenReturn("dummy");

        String raw = service.createEmailVerificationToken(userId);

        // URL-safe Base64 without padding uses only [A-Za-z0-9\-_].
        assertThat(raw).matches("[A-Za-z0-9\\-_]+");
    }

    @Test
    void createPasswordResetToken_rawTokenIs43CharactersLong() {
        UUID userId = UUID.randomUUID();
        when(redisTemplate.execute(
                        any(RedisScript.class),
                        anyList(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString()))
                .thenReturn("dummy");

        String raw = service.createPasswordResetToken(userId);

        assertThat(raw).hasSize(43);
    }

    // -----------------------------------------------------------------------
    // Consume path (unchanged behaviour)
    // -----------------------------------------------------------------------

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
                .execute(any(RedisScript.class), eq(List.of(EMAIL_PREFIX + sha256(raw))));
    }

    @Test
    void consumeEmailVerificationToken_throwsWhenAbsent() {
        when(redisTemplate.execute(any(RedisScript.class), anyList())).thenReturn(null);

        assertThatThrownBy(() -> service.consumeEmailVerificationToken("ghost"))
                .isInstanceOf(TokenNotFoundException.class);
        verify(redisTemplate, never()).delete(any(String.class));
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
