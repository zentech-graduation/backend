package com.app.modules.hashtag.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Binds hashtag index-sync events to the hashtag index sync queue. */
@Configuration
public class HashtagRabbitBindingConfig {

    @Bean
    Binding hashtagIndexSyncBinding(
            Queue hashtagIndexSyncQueue, TopicExchange socialEventsExchange) {
        return BindingBuilder.bind(hashtagIndexSyncQueue)
                .to(socialEventsExchange)
                .with("hashtag.index.#");
    }
}
