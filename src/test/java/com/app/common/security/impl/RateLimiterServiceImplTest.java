package com.app.common.security.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceImplTest {

    @Mock private StringRedisTemplate redisTemplate;

    private RateLimiterServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RateLimiterServiceImpl(redisTemplate);
    }

    @Test
    void isAllowed_underLimit_returnsTrue() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(3L);

        assertThat(service.isAllowed("login:1.1.1.1:a@b.c", 5, 900)).isTrue();
    }

    @Test
    void isAllowed_atLimit_returnsTrue() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(5L);

        assertThat(service.isAllowed("login:1.1.1.1:a@b.c", 5, 900)).isTrue();
    }

    @Test
    void isAllowed_overLimit_returnsFalse() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(6L);

        assertThat(service.isAllowed("login:1.1.1.1:a@b.c", 5, 900)).isFalse();
    }

    @Test
    void isAllowed_scriptReturnsNull_returnsFalse() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(null);

        assertThat(service.isAllowed("login:1.1.1.1:a@b.c", 5, 900)).isFalse();
    }
}
