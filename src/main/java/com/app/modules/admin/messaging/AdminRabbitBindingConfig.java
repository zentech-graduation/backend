package com.app.modules.admin.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Binds moderation notification-bearing events to the dedicated admin notification queue. */
@Configuration
public class AdminRabbitBindingConfig {

    @Bean
    Binding userWarnedNotificationBinding(
            Queue adminNotificationQueue, TopicExchange socialEventsExchange) {
        return BindingBuilder.bind(adminNotificationQueue)
                .to(socialEventsExchange)
                .with(AdminEventTypes.USER_WARNED_V1);
    }
}
