package com.app.modules.post.live;

import java.util.UUID;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares this instance's ephemeral fanout queue for live post events.
 *
 * <p>Each application instance binds its own auto-delete queue to the post live fanout exchange, so
 * every instance receives all post live events and can push them to the sessions it locally holds.
 * The queue is durable (RabbitMQ 4.x rejects non-durable, non-exclusive queue declarations) but
 * still auto-deletes when the instance's consumer disconnects.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.post.live", name = "enabled", havingValue = "true")
public class PostLiveServerQueueInitializer {

    private final String serverId = UUID.randomUUID().toString();
    private final String queueName = "post.live." + serverId;

    public String getServerId() {
        return serverId;
    }

    public String getQueueName() {
        return queueName;
    }

    @Bean
    Queue postLiveServerQueue() {
        return QueueBuilder.durable(queueName).autoDelete().build();
    }

    @Bean
    Binding postLiveServerQueueBinding(
            Queue postLiveServerQueue, FanoutExchange postLiveEventsExchange) {
        return BindingBuilder.bind(postLiveServerQueue).to(postLiveEventsExchange);
    }
}
