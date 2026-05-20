package com.app.common.config.rabbit;

import java.util.List;

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
 * <p>Only queues with an implemented consumer are declared here; future side-effect queues remain
 * constants until their consumers exist.
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

    public static final String USER_REGISTERED_V1 = "user.registered.v1";
    public static final String AUTH_EMAIL_VERIFICATION_REQUESTED_V1 =
            "auth.email-verification.requested.v1";
    public static final String AUTH_PASSWORD_RESET_REQUESTED_V1 =
            "auth.password-reset.requested.v1";
    public static final String AUTH_PASSWORD_CHANGED_V1 = "auth.password-changed.v1";
    public static final String AUTH_OAUTH_ACCOUNT_NO_PASSWORD_V1 =
            "auth.oauth-account-no-password.v1";

    public static final List<String> MAIL_EVENT_ROUTING_KEYS =
            List.of(
                    USER_REGISTERED_V1,
                    AUTH_EMAIL_VERIFICATION_REQUESTED_V1,
                    AUTH_PASSWORD_RESET_REQUESTED_V1,
                    AUTH_PASSWORD_CHANGED_V1,
                    AUTH_OAUTH_ACCOUNT_NO_PASSWORD_V1);

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
    Binding mailUserRegisteredBinding(Queue mailQueue, TopicExchange socialEventsExchange) {
        return bindMailQueue(mailQueue, socialEventsExchange, USER_REGISTERED_V1);
    }

    @Bean
    Binding mailEmailVerificationRequestedBinding(
            Queue mailQueue, TopicExchange socialEventsExchange) {
        return bindMailQueue(mailQueue, socialEventsExchange, AUTH_EMAIL_VERIFICATION_REQUESTED_V1);
    }

    @Bean
    Binding mailPasswordResetRequestedBinding(Queue mailQueue, TopicExchange socialEventsExchange) {
        return bindMailQueue(mailQueue, socialEventsExchange, AUTH_PASSWORD_RESET_REQUESTED_V1);
    }

    @Bean
    Binding mailPasswordChangedBinding(Queue mailQueue, TopicExchange socialEventsExchange) {
        return bindMailQueue(mailQueue, socialEventsExchange, AUTH_PASSWORD_CHANGED_V1);
    }

    @Bean
    Binding mailOauthAccountNoPasswordBinding(Queue mailQueue, TopicExchange socialEventsExchange) {
        return bindMailQueue(mailQueue, socialEventsExchange, AUTH_OAUTH_ACCOUNT_NO_PASSWORD_V1);
    }

    @Bean
    Binding mailDeadLetterBinding(
            Queue mailDeadLetterQueue, TopicExchange socialEventsDeadLetterExchange) {
        return BindingBuilder.bind(mailDeadLetterQueue)
                .to(socialEventsDeadLetterExchange)
                .with(MAIL_DEAD_LETTER_ROUTING_KEY);
    }

    private static Binding bindMailQueue(
            Queue mailQueue, TopicExchange socialEventsExchange, String routingKey) {
        return BindingBuilder.bind(mailQueue).to(socialEventsExchange).with(routingKey);
    }
}
