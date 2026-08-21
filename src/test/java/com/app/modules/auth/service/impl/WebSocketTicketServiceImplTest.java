package com.app.modules.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import com.app.common.exception.AppException;

@ExtendWith(MockitoExtension.class)
class WebSocketTicketServiceImplTest {

    private static final String ACCESS_TOKEN = "header.payload.signature";

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private WebSocketTicketServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WebSocketTicketServiceImpl(redisTemplate);
    }

    @Test
    void issueTicket_storesTheAccessTokenWith30sTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        String ticket = service.issueTicket(ACCESS_TOKEN);

        assertThat(ticket).hasSize(64);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(keyCaptor.capture(), eq(ACCESS_TOKEN), eq(Duration.ofSeconds(30)));
        assertThat(keyCaptor.getValue()).isEqualTo("auth:ws-ticket:" + ticket);
    }

    @Test
    void issueTicket_returnsADistinctValueEveryTime() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        assertThat(service.issueTicket(ACCESS_TOKEN))
                .isNotEqualTo(service.issueTicket(ACCESS_TOKEN));
    }

    @Test
    void consumeTicket_present_returnsTheStoredAccessToken() {
        // The stored value must be the access token itself, not a user id: the revocation sweep
        // re-resolves whatever the handshake recorded, so a user id would leave it unable to check.
        when(redisTemplate.execute(any(RedisScript.class), anyList())).thenReturn(ACCESS_TOKEN);

        assertThat(service.consumeTicket("someticket")).isEqualTo(ACCESS_TOKEN);
    }

    @Test
    void consumeTicket_absentOrAlreadyRedeemed_isRejected() {
        // The Lua script returns nil for an unknown, expired, or already-redeemed key alike, so one
        // branch covers all three.
        when(redisTemplate.execute(any(RedisScript.class), anyList())).thenReturn(null);

        assertThatThrownBy(() -> service.consumeTicket("someticket"))
                .isInstanceOf(AppException.class);
    }

    @Test
    void consumeTicket_blank_isRejectedWithoutTouchingRedis() {
        assertThatThrownBy(() -> service.consumeTicket("  ")).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> service.consumeTicket(null)).isInstanceOf(AppException.class);
    }
}
