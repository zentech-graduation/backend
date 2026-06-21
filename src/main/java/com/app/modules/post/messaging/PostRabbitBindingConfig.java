package com.app.modules.post.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Binds post index-sync events to the post index sync queue. */
@Configuration
public class PostRabbitBindingConfig {

    @Bean
    Binding postIndexSyncBinding(Queue postIndexSyncQueue, TopicExchange socialEventsExchange) {
        return BindingBuilder.bind(postIndexSyncQueue)
                .to(socialEventsExchange)
                .with("post.index.#");
    }
}
