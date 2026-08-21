package com.app.modules.message.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Message module settings bound from the {@code app.message} namespace. */
@ConfigurationProperties(prefix = "app.message")
public record MessageProperties(int idempotencyTtlHours) {

    public MessageProperties {
        idempotencyTtlHours = idempotencyTtlHours <= 0 ? 24 : idempotencyTtlHours;
    }
}
