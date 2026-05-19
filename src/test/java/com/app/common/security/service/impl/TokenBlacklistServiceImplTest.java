package com.app.common.security.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceImplTest {

    private static final String KEY_PREFIX = "auth:blacklist:";

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private TokenBlacklistServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new TokenBlacklistServiceImpl(redisTemplate);
    }

    @Test
    void blacklist_positiveTtl_storesKeyWithExpiry() {
        service.blacklist("jti-123", 600L);

        verify(valueOps).set(eq(KEY_PREFIX + "jti-123"), eq("1"), eq(Duration.ofSeconds(600)));
    }

    @Test
    void blacklist_zeroTtl_doesNotStore() {
        service.blacklist("jti-123", 0L);

        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void blacklist_negativeTtl_doesNotStore() {
        service.blacklist("jti-123", -10L);

        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void blacklist_nullJti_doesNotStore() {
        service.blacklist(null, 600L);

        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void blacklist_blankJti_doesNotStore() {
        service.blacklist("   ", 600L);

        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void isBlacklisted_keyExists_returnsTrue() {
        when(redisTemplate.hasKey(KEY_PREFIX + "jti-x")).thenReturn(Boolean.TRUE);

        assertThat(service.isBlacklisted("jti-x")).isTrue();
    }

    @Test
    void isBlacklisted_keyAbsent_returnsFalse() {
        when(redisTemplate.hasKey(KEY_PREFIX + "jti-y")).thenReturn(Boolean.FALSE);

        assertThat(service.isBlacklisted("jti-y")).isFalse();
    }

    @Test
    void isBlacklisted_redisReturnsNull_returnsFalse() {
        when(redisTemplate.hasKey(KEY_PREFIX + "jti-z")).thenReturn(null);

        assertThat(service.isBlacklisted("jti-z")).isFalse();
    }

    @Test
    void isBlacklisted_nullJti_returnsFalse() {
        assertThat(service.isBlacklisted(null)).isFalse();
    }

    @Test
    void isBlacklisted_blankJti_returnsFalse() {
        assertThat(service.isBlacklisted("")).isFalse();
    }

    @Test
    void blacklist_redisThrowsDataAccessException_throwsAppExceptionWithInternalError() {
        doThrow(new DataAccessException("simulated redis failure") {})
                .when(valueOps)
                .set(anyString(), anyString(), any(Duration.class));

        assertThatThrownBy(() -> service.blacklist("jti-fail", 300L))
                .isInstanceOf(AppException.class)
                .satisfies(
                        ex ->
                                assertThat(((AppException) ex).getErrorCode())
                                        .isEqualTo(ApiErrorCode.INTERNAL_ERROR));
    }

    @Test
    void blacklist_redisSucceeds_noExceptionAndSetCalledWithCorrectArgs() {
        service.blacklist("jti-ok", 120L);

        verify(valueOps).set(eq(KEY_PREFIX + "jti-ok"), eq("1"), eq(Duration.ofSeconds(120)));
    }
}
