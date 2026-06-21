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
    public static final String NOTIFICATION_DEAD_LETTER_QUEUE = "notification.dlq";
    public static final String NOTIFICATION_DEAD_LETTER_ROUTING_KEY = "notification.dead-letter";

    public static final String HASHTAG_INDEX_SYNC_QUEUE = "hashtag.index.sync";
    public static final String HASHTAG_INDEX_SYNC_DEAD_LETTER_QUEUE = "hashtag.index.sync.dlq";
    public static final String HASHTAG_INDEX_DEAD_LETTER_ROUTING_KEY = "hashtag.index.dead-letter";

    public static final String POST_INDEX_SYNC_QUEUE = "post.index.sync";
    public static final String POST_INDEX_SYNC_DEAD_LETTER_QUEUE = "post.index.sync.dlq";
    public static final String POST_INDEX_DEAD_LETTER_ROUTING_KEY = "post.index.dead-letter";

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

    @Bean
    Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE).build();
    }

    @Bean
    Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(NOTIFICATION_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding notificationDeadLetterBinding(
            Queue notificationDeadLetterQueue, TopicExchange socialEventsDeadLetterExchange) {
        return BindingBuilder.bind(notificationDeadLetterQueue)
                .to(socialEventsDeadLetterExchange)
                .with(NOTIFICATION_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    Queue hashtagIndexSyncQueue() {
        return QueueBuilder.durable(HASHTAG_INDEX_SYNC_QUEUE).build();
    }

    @Bean
    Queue hashtagIndexSyncDeadLetterQueue() {
        return QueueBuilder.durable(HASHTAG_INDEX_SYNC_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding hashtagIndexSyncDeadLetterBinding(
            Queue hashtagIndexSyncDeadLetterQueue, TopicExchange socialEventsDeadLetterExchange) {
        return BindingBuilder.bind(hashtagIndexSyncDeadLetterQueue)
                .to(socialEventsDeadLetterExchange)
                .with(HASHTAG_INDEX_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    Queue postIndexSyncQueue() {
        return QueueBuilder.durable(POST_INDEX_SYNC_QUEUE).build();
    }

    @Bean
    Queue postIndexSyncDeadLetterQueue() {
        return QueueBuilder.durable(POST_INDEX_SYNC_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding postIndexSyncDeadLetterBinding(
            Queue postIndexSyncDeadLetterQueue, TopicExchange socialEventsDeadLetterExchange) {
        return BindingBuilder.bind(postIndexSyncDeadLetterQueue)
                .to(socialEventsDeadLetterExchange)
                .with(POST_INDEX_DEAD_LETTER_ROUTING_KEY);
    }
}
