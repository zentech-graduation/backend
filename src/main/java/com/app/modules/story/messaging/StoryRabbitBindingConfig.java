package com.app.modules.story.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Binds story notification-bearing events to the dedicated story notification queue. */
@Configuration
public class StoryRabbitBindingConfig {

    @Bean
    Binding storyViewedNotificationBinding(
            Queue storyNotificationQueue, TopicExchange socialEventsExchange) {
        return BindingBuilder.bind(storyNotificationQueue)
                .to(socialEventsExchange)
                .with(StoryEventTypes.STORY_VIEWED_V1);
    }
}
