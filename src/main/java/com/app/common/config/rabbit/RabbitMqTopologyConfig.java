package com.app.common.config.rabbit;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the active RabbitMQ topology for domain events.
 *
 * <p>Only shared broker infrastructure and active queues live here. Module-specific event bindings
 * stay with their owning module.
 */
@Configuration
public class RabbitMqTopologyConfig {

    public static final String SOCIAL_EVENTS_EXCHANGE = "social.events";
    public static final String SOCIAL_EVENTS_DEAD_LETTER_EXCHANGE = "social.events.dlx";

    public static final String MAIL_QUEUE = "mail.queue";
    public static final String MAIL_DEAD_LETTER_QUEUE = "mail.dlq";
    public static final String MAIL_DEAD_LETTER_ROUTING_KEY = "mail.dead-letter";

    public static final String NOTIFICATION_QUEUE = "notification.queue";
    public static final String AUDIT_LOG_QUEUE = "audit-log.queue";
    public static final String MODERATION_QUEUE = "moderation.queue";
    public static final String SEARCH_INDEX_QUEUE = "search-index.queue";

    @Bean
    TopicExchange socialEventsExchange() {
        return ExchangeBuilder.topicExchange(SOCIAL_EVENTS_EXCHANGE).durable(true).build();
    }

    @Bean
    TopicExchange socialEventsDeadLetterExchange() {
        return ExchangeBuilder.topicExchange(SOCIAL_EVENTS_DEAD_LETTER_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    Queue mailQueue() {
        return QueueBuilder.durable(MAIL_QUEUE).build();
    }

    @Bean
    Queue mailDeadLetterQueue() {
        return QueueBuilder.durable(MAIL_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding mailDeadLetterBinding(
            Queue mailDeadLetterQueue, TopicExchange socialEventsDeadLetterExchange) {
        return BindingBuilder.bind(mailDeadLetterQueue)
                .to(socialEventsDeadLetterExchange)
                .with(MAIL_DEAD_LETTER_ROUTING_KEY);
    }
}
