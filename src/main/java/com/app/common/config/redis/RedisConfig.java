package com.app.common.config.redis;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis client wiring. Exposes a single {@link StringRedisTemplate} for string-only operations such
 * as the JWT blacklist and the auth rate limiter; no generic value template is declared because no
 * binary or JSON-serialised values are stored from application code.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
