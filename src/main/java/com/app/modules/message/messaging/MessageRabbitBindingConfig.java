package com.app.modules.message.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Binds message notification-bearing events to the dedicated message notification queue. */
@Configuration
public class MessageRabbitBindingConfig {

    @Bean
    Binding messageSentNotificationBinding(
            Queue messageNotificationQueue, TopicExchange socialEventsExchange) {
        return BindingBuilder.bind(messageNotificationQueue)
                .to(socialEventsExchange)
                .with(MessageEventTypes.MESSAGE_SENT_V1);
    }
}
