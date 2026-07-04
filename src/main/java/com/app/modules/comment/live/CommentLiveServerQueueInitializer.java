package com.app.modules.comment.live;

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
 * Declares this instance's ephemeral fanout queue for live comment events.
 *
 * <p>Each application instance binds its own auto-delete queue to the comment live fanout exchange,
 * so every instance receives all comment events and can push them to the sessions it locally holds.
 * The queue is non-durable and auto-deletes when the instance disconnects.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.comment.live", name = "enabled", havingValue = "true")
public class CommentLiveServerQueueInitializer {

    private final String serverId = UUID.randomUUID().toString();
    private final String queueName = "comment.live." + serverId;

    public String getServerId() {
        return serverId;
    }

    public String getQueueName() {
        return queueName;
    }

    @Bean
    Queue commentLiveServerQueue() {
        return QueueBuilder.nonDurable(queueName).autoDelete().build();
    }

    @Bean
    Binding commentLiveServerQueueBinding(
            Queue commentLiveServerQueue, FanoutExchange commentLiveEventsExchange) {
        return BindingBuilder.bind(commentLiveServerQueue).to(commentLiveEventsExchange);
    }
}
