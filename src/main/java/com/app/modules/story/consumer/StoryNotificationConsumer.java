package com.app.modules.story.consumer;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.stereotype.Component;

import com.app.common.config.rabbit.RabbitMqTopologyConfig;
import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.inbox.service.ProcessedMessageService;
import com.app.common.messaging.DeadLetterPublisher;
import com.app.common.messaging.DomainEventMessageParser;
import com.app.common.messaging.config.ConsumerRetryProperties;
import com.app.common.messaging.exception.PermanentMessageException;
import com.app.common.outbox.model.DomainEventEnvelope;
import com.app.modules.notification.entity.enums.NotificationType;
import com.app.modules.notification.service.NotificationService;
import com.rabbitmq.client.Channel;

/**
 * RabbitMQ consumer that creates {@code STORY_VIEW} notifications from {@code story.viewed.v1}
 * events.
 *
 * <p>The single notification producer for the story module. The recipient (story owner) is resolved
 * from the event {@code data} payload, not from the aggregate id. {@code
 * NotificationService.create} already suppresses self-notifications, blocked actors, and
 * toggled-off preferences (though {@code STORY_VIEW} has no toggle), so no extra guards are applied
 * here. Uses manual acknowledgement with bounded retry and dead-letter routing.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.story.consumer",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class StoryNotificationConsumer {

    static final String CONSUMER_NAME = "story-notification-consumer";

    private static final Logger log = LoggerFactory.getLogger(StoryNotificationConsumer.class);
    private static final String ENTITY_TYPE = "story";

    private final DomainEventMessageParser parser;
    private final ProcessedMessageService processedMessageService;
    private final NotificationService notificationService;
    private final ConsumerRetryProperties retryProperties;
    private final DeadLetterPublisher deadLetterPublisher;

    public StoryNotificationConsumer(
            DomainEventMessageParser parser,
            ProcessedMessageService processedMessageService,
            NotificationService notificationService,
            ConsumerRetryProperties retryProperties,
            DeadLetterPublisher deadLetterPublisher) {
        this.parser = parser;
        this.processedMessageService = processedMessageService;
        this.notificationService = notificationService;
        this.retryProperties = retryProperties;
        this.deadLetterPublisher = deadLetterPublisher;
    }

    @RabbitListener(queues = RabbitMqTopologyConfig.STORY_NOTIFICATION_QUEUE)
    public void consume(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            DomainEventEnvelope event = parser.parse(message);
            validateEnvelope(event);
            processWithRetry(event);
            channel.basicAck(deliveryTag, false);
        } catch (PermanentMessageException ex) {
            routeToDlqOrRequeue(message, channel, deliveryTag, ex);
        } catch (RuntimeException ex) {
            routeToDlqOrRequeue(message, channel, deliveryTag, ex);
        }
    }

    private void processWithRetry(DomainEventEnvelope event) {
        int maxAttempts = retryProperties.resolvedMaxAttempts();
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                processedMessageService.processOnce(
                        CONSUMER_NAME, event.eventId(), event.eventType(), () -> dispatch(event));
                return;
            } catch (RuntimeException ex) {
                if (isPermanent(ex) || !isTransient(ex)) {
                    throw ex;
                }
                lastFailure = ex;
                if (attempt >= maxAttempts) {
                    break;
                }
                sleepBeforeRetry(attempt);
            }
        }
        throw lastFailure;
    }

    private void dispatch(DomainEventEnvelope event) {
        Map<String, Object> data = event.data();
        notificationService.create(
                event.actorId(),
                uuid(data.get("ownerId")),
                NotificationType.STORY_VIEW,
                ENTITY_TYPE,
                uuid(data.get("storyId")));
    }

    private void validateEnvelope(DomainEventEnvelope event) {
        if (event == null || event.eventId() == null) {
            throw new PermanentMessageException("Event envelope or id is null");
        }
        if (event.eventType() == null || event.eventType().isBlank()) {
            throw new PermanentMessageException("Event type is missing");
        }
        if (event.data() == null || event.data().get("storyId") == null) {
            throw new PermanentMessageException("Story id is missing from event data");
        }
        if (event.data().get("ownerId") == null) {
            throw new PermanentMessageException("Owner id is missing from event data");
        }
    }

    private void sleepBeforeRetry(int attempt) {
        Duration backoff = retryProperties.retryBackoffForAttempt(attempt);
        if (backoff.isZero()) {
            return;
        }
        try {
            Thread.sleep(backoff.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during story notification retry backoff", ex);
        }
    }

    private void routeToDlqOrRequeue(
            Message message, Channel channel, long deliveryTag, RuntimeException failure)
            throws IOException {
        try {
            deadLetterPublisher.publish(
                    message,
                    RabbitMqTopologyConfig.STORY_NOTIFICATION_DEAD_LETTER_ROUTING_KEY,
                    failure.getMessage());
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException dlqFailure) {
            log.warn(
                    "Failed to publish story notification event to DLQ; requeueing: {}",
                    dlqFailure.getMessage());
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private static boolean isPermanent(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof PermanentMessageException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isTransient(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof DataAccessException
                    || current instanceof RedisSystemException
                    || current instanceof AmqpException) {
                return true;
            }
            if (current instanceof AppException appException) {
                return appException.getErrorCode() == ApiErrorCode.SERVICE_UNAVAILABLE;
            }
            current = current.getCause();
        }
        return true;
    }

    private static UUID uuid(Object value) {
        return value == null ? null : UUID.fromString(value.toString());
    }
}
