package com.app.common.config.rabbit;

import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures RabbitMQ publisher behavior shared by outbox publishing. */
@Configuration
public class RabbitMqPublisherConfig {

    @Bean
    RabbitTemplateCustomizer rabbitTemplateReturnsCallback() {
        return rabbitTemplate -> rabbitTemplate.setReturnsCallback(returned -> {});
    }
}
