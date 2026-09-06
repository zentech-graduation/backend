package com.app.modules.message.consumer;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
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
import com.app.common.messaging.DomainEventMessageParser;
import com.app.common.messaging.config.ConsumerRetryProperties;
import com.app.common.messaging.exception.PermanentMessageException;
import com.app.common.outbox.model.DomainEventEnvelope;
import com.app.modules.message.repository.ConversationParticipantRepository;
import com.app.modules.notification.entity.enums.NotificationType;
import com.app.modules.notification.service.NotificationService;
import com.rabbitmq.client.Channel;

/**
 * RabbitMQ consumer that creates {@code MESSAGE} notifications from {@code message.sent.v1} events.
 *
 * <p>The single notification producer for the message module. Recipients are the conversation's
 * active participants at consume time, resolved by a fresh repository query rather than a list
 * embedded in the event payload, excluding the sender. {@code NotificationService.create} already
 * suppresses self-notifications, blocked actors, and toggled-off preferences ({@code
 * notify_messages}), so those are defense-in-depth, not the primary gate. Uses manual
 * acknowledgement with bounded retry; a permanent or retry-exhausted failure nacks without requeue
 * so the broker routes the message to the dead-letter queue per the topology's declared policy.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.message.consumer",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class MessageNotificationConsumer {

    static final String CONSUMER_NAME = "message-notification-consumer";

    private static final Logger log = LoggerFactory.getLogger(MessageNotificationConsumer.class);
    private static final String ENTITY_TYPE = "message";

    private final DomainEventMessageParser parser;
    private final ProcessedMessageService processedMessageService;
    private final ConversationParticipantRepository participantRepository;
    private final NotificationService notificationService;
    private final ConsumerRetryProperties retryProperties;

    public MessageNotificationConsumer(
            DomainEventMessageParser parser,
            ProcessedMessageService processedMessageService,
            ConversationParticipantRepository participantRepository,
            NotificationService notificationService,
            ConsumerRetryProperties retryProperties) {
        this.parser = parser;
        this.processedMessageService = processedMessageService;
        this.participantRepository = participantRepository;
        this.notificationService = notificationService;
        this.retryProperties = retryProperties;
    }

    @RabbitListener(queues = RabbitMqTopologyConfig.MESSAGE_NOTIFICATION_QUEUE)
    public void consume(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            DomainEventEnvelope event = parser.parse(message);
            validateEnvelope(event);
            processWithRetry(event);
            channel.basicAck(deliveryTag, false);
        } catch (PermanentMessageException ex) {
            log.warn(
                    "Message notification event permanently invalid, dead-lettering: {}",
                    ex.getMessage());
            channel.basicNack(deliveryTag, false, false);
        } catch (RuntimeException ex) {
            log.warn(
                    "Message notification event failed after exhausting retries, dead-lettering: {}",
                    ex.getMessage());
            channel.basicNack(deliveryTag, false, false);
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
        UUID senderId = event.actorId();
        UUID conversationId = uuid(data.get("conversationId"));
        UUID messageId = uuid(data.get("messageId"));
        List<UUID> activeParticipantIds =
                participantRepository.findActiveUserIdsByConversationId(conversationId);
        for (UUID recipientId : activeParticipantIds) {
            if (recipientId.equals(senderId)) {
                continue;
            }
            notificationService.create(
                    senderId, recipientId, NotificationType.MESSAGE, ENTITY_TYPE, messageId, null);
        }
    }

    private void validateEnvelope(DomainEventEnvelope event) {
        if (event == null || event.eventId() == null) {
            throw new PermanentMessageException("Event envelope or id is null");
        }
        if (event.eventType() == null || event.eventType().isBlank()) {
            throw new PermanentMessageException("Event type is missing");
        }
        if (event.data() == null || event.data().get("conversationId") == null) {
            throw new PermanentMessageException("Conversation id is missing from event data");
        }
        if (event.data().get("messageId") == null) {
            throw new PermanentMessageException("Message id is missing from event data");
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
            throw new RuntimeException("Interrupted during message notification retry backoff", ex);
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
