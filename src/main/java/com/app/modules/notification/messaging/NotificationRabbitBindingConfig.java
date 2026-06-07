package com.app.modules.notification.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.app.modules.social.messaging.SocialEventTypes;

/** Binds social follow events to the notification queue. */
@Configuration
public class NotificationRabbitBindingConfig {

    @Bean
    Binding notificationFollowedBinding(
            Queue notificationQueue, TopicExchange socialEventsExchange) {
        return BindingBuilder.bind(notificationQueue)
                .to(socialEventsExchange)
                .with(SocialEventTypes.USER_FOLLOWED_V1);
    }

    @Bean
    Binding notificationFollowRequestedBinding(
            Queue notificationQueue, TopicExchange socialEventsExchange) {
        return BindingBuilder.bind(notificationQueue)
                .to(socialEventsExchange)
                .with(SocialEventTypes.USER_FOLLOW_REQUESTED_V1);
    }
}
