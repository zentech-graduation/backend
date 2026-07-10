package com.app.common.config.rabbit;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
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

    public static final String COMMENT_LIVE_EVENTS_EXCHANGE = "comment.live.events";
    public static final String COMMENT_NOTIFICATION_QUEUE = "comment.notification.queue";
    public static final String COMMENT_NOTIFICATION_DEAD_LETTER_QUEUE = "comment.notification.dlq";
    public static final String COMMENT_NOTIFICATION_DEAD_LETTER_ROUTING_KEY =
            "comment.notification.dead-letter";

    public static final String REC_EVENTS_QUEUE = "rec.events.queue";
    public static final String REC_EVENTS_DEAD_LETTER_QUEUE = "rec.events.dlq";
    public static final String REC_EVENTS_DEAD_LETTER_ROUTING_KEY = "rec.events.dead-letter";

    public static final String REC_TRENDING_QUEUE = "rec.trending.queue";
    public static final String REC_TRENDING_DEAD_LETTER_QUEUE = "rec.trending.dlq";
    public static final String REC_TRENDING_DEAD_LETTER_ROUTING_KEY = "rec.trending.dead-letter";

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

    @Bean
    FanoutExchange commentLiveEventsExchange() {
        return ExchangeBuilder.fanoutExchange(COMMENT_LIVE_EVENTS_EXCHANGE).durable(true).build();
    }

    // Exchange-to-exchange: the topic bus routes every comment.* event into the live fanout so the
    // outbox publishes once and the broker fans out to both the notification queue and the live
    // tier.
    @Bean
    Binding commentLiveExchangeBinding(
            FanoutExchange commentLiveEventsExchange, TopicExchange socialEventsExchange) {
        return BindingBuilder.bind(commentLiveEventsExchange)
                .to(socialEventsExchange)
                .with("comment.#");
    }

    @Bean
    Queue commentNotificationQueue() {
        return QueueBuilder.durable(COMMENT_NOTIFICATION_QUEUE)
                .withArgument("x-dead-letter-exchange", SOCIAL_EVENTS_DEAD_LETTER_EXCHANGE)
                .withArgument(
                        "x-dead-letter-routing-key", COMMENT_NOTIFICATION_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue commentNotificationDeadLetterQueue() {
        return QueueBuilder.durable(COMMENT_NOTIFICATION_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding commentNotificationDeadLetterBinding(
            Queue commentNotificationDeadLetterQueue,
            TopicExchange socialEventsDeadLetterExchange) {
        return BindingBuilder.bind(commentNotificationDeadLetterQueue)
                .to(socialEventsDeadLetterExchange)
                .with(COMMENT_NOTIFICATION_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    Queue recEventsQueue() {
        return QueueBuilder.durable(REC_EVENTS_QUEUE).build();
    }

    @Bean
    Queue recEventsDeadLetterQueue() {
        return QueueBuilder.durable(REC_EVENTS_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding recEventsDeadLetterBinding(
            Queue recEventsDeadLetterQueue, TopicExchange socialEventsDeadLetterExchange) {
        return BindingBuilder.bind(recEventsDeadLetterQueue)
                .to(socialEventsDeadLetterExchange)
                .with(REC_EVENTS_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    Queue recTrendingQueue() {
        return QueueBuilder.durable(REC_TRENDING_QUEUE).build();
    }

    @Bean
    Queue recTrendingDeadLetterQueue() {
        return QueueBuilder.durable(REC_TRENDING_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding recTrendingDeadLetterBinding(
            Queue recTrendingDeadLetterQueue, TopicExchange socialEventsDeadLetterExchange) {
        return BindingBuilder.bind(recTrendingDeadLetterQueue)
                .to(socialEventsDeadLetterExchange)
                .with(REC_TRENDING_DEAD_LETTER_ROUTING_KEY);
    }
}
