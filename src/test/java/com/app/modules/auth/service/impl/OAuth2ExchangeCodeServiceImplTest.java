package com.app.modules.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
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

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;

@ExtendWith(MockitoExtension.class)
class OAuth2ExchangeCodeServiceImplTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private OAuth2ExchangeCodeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OAuth2ExchangeCodeServiceImpl(redisTemplate);
    }

    @Test
    void storeExchangeCode_persistsUserIdWith120sTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        UUID userId = UUID.randomUUID();

        String code = service.storeExchangeCode(userId);

        assertThat(code).hasSize(64);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps)
                .set(keyCaptor.capture(), eq(userId.toString()), eq(Duration.ofSeconds(120)));
        assertThat(keyCaptor.getValue()).isEqualTo("auth:oauth2:exchange:" + code);
    }

    @Test
    void consumeExchangeCode_present_returnsUserId() {
        UUID userId = UUID.randomUUID();
        when(redisTemplate.execute(any(RedisScript.class), anyList()))
                .thenReturn(userId.toString());

        assertThat(service.consumeExchangeCode("somecode")).isEqualTo(userId);
    }

    @Test
    void consumeExchangeCode_absent_throwsExchangeCodeInvalid() {
        when(redisTemplate.execute(any(RedisScript.class), anyList())).thenReturn(null);

        assertThatThrownBy(() -> service.consumeExchangeCode("missing"))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_OAUTH2_EXCHANGE_CODE_INVALID);
    }
}
