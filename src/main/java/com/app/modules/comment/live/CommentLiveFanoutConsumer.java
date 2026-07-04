package com.app.modules.comment.live;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.app.common.messaging.DomainEventMessageParser;
import com.app.common.outbox.model.DomainEventEnvelope;
import com.app.modules.comment.observability.CommentMetrics;

import io.micrometer.core.instrument.Timer;

/**
 * Pushes live comment events received on this instance's fanout queue to local STOMP subscribers.
 *
 * <p>Delivery is best-effort: the SimpleBroker routes each message to the sessions subscribed to
 * the post's destination on this instance. Failures are logged and dropped; client reconnect plus
 * REST resync recovers any missed events.
 */
@Component
@ConditionalOnProperty(prefix = "app.comment.live", name = "enabled", havingValue = "true")
public class CommentLiveFanoutConsumer {

    private static final Logger log = LoggerFactory.getLogger(CommentLiveFanoutConsumer.class);

    private final DomainEventMessageParser parser;
    private final SimpMessagingTemplate messagingTemplate;
    private final CommentMetrics metrics;
    private final CommentLiveServerQueueInitializer serverQueueInitializer;

    public CommentLiveFanoutConsumer(
            DomainEventMessageParser parser,
            SimpMessagingTemplate messagingTemplate,
            CommentMetrics metrics,
            CommentLiveServerQueueInitializer serverQueueInitializer) {
        this.parser = parser;
        this.messagingTemplate = messagingTemplate;
        this.metrics = metrics;
        this.serverQueueInitializer = serverQueueInitializer;
    }

    @RabbitListener(queues = "#{commentLiveServerQueueInitializer.queueName}", ackMode = "NONE")
    public void consume(Message message) {
        Timer.Sample sample = Timer.start();
        MDC.put("serverId", serverQueueInitializer.getServerId());
        try {
            DomainEventEnvelope event = parser.parse(message);
            MDC.put("eventId", String.valueOf(event.eventId()));
            Object postId = event.data().get("postId");
            if (postId == null) {
                return;
            }
            MDC.put("postId", postId.toString());
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType", event.eventType());
            payload.put("data", event.data());
            messagingTemplate.convertAndSend(
                    "/topic/comments." + UUID.fromString(postId.toString()) + ".events",
                    (Object) payload);
        } catch (RuntimeException ex) {
            metrics.wsPushFailure();
            log.warn("Failed to push live comment event: {}", ex.getMessage());
        } finally {
            sample.stop(metrics.fanoutLatency());
            MDC.remove("eventId");
            MDC.remove("postId");
            MDC.remove("serverId");
        }
    }
}
