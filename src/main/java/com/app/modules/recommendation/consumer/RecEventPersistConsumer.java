package com.app.modules.recommendation.consumer;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.stereotype.Component;

import com.app.common.config.rabbit.RabbitMqTopologyConfig;
import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.inbox.enums.ProcessedMessageResult;
import com.app.common.inbox.service.ProcessedMessageService;
import com.app.common.messaging.DeadLetterPublisher;
import com.app.common.messaging.DomainEventMessageParser;
import com.app.common.messaging.config.ConsumerRetryProperties;
import com.app.common.messaging.exception.PermanentMessageException;
import com.app.common.outbox.model.DomainEventEnvelope;
import com.app.modules.recommendation.dto.ImpressionBatchEvent;
import com.app.modules.recommendation.messaging.RecommendationEventTypes;
import com.app.modules.recommendation.observability.RecommendationMetrics;
import com.app.modules.recommendation.repository.RecommendationEventJdbcRepository;
import com.rabbitmq.client.Channel;

/**
 * RabbitMQ consumer that persists recommendation interaction and impression-batch events into
 * PostgreSQL.
 *
 * <p>Uses manual acknowledgement: ack after idempotent duplicate detection or successful side
 * effect, route poison messages to DLQ, and nack with requeue if DLQ publishing itself fails. The
 * {@code user_events} row is written for every interaction; the {@code impressions} rows are
 * written for every impression-batch item, each with a deterministic id derived from the client
 * event id so a partial-failure replay never duplicates rows.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.recommendation.consumer",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class RecEventPersistConsumer {

    static final String CONSUMER_NAME = "rec-event-persist-consumer";

    private static final Logger log = LoggerFactory.getLogger(RecEventPersistConsumer.class);

    private final DomainEventMessageParser parser;
    private final ProcessedMessageService processedMessageService;
    private final DeadLetterPublisher deadLetterPublisher;
    private final ConsumerRetryProperties retryProperties;
    private final RecommendationEventJdbcRepository eventRepository;
    private final RecommendationMetrics metrics;
    private final Sleeper sleeper;

    @Autowired
    public RecEventPersistConsumer(
            DomainEventMessageParser parser,
            ProcessedMessageService processedMessageService,
            DeadLetterPublisher deadLetterPublisher,
            ConsumerRetryProperties retryProperties,
            RecommendationEventJdbcRepository eventRepository,
            RecommendationMetrics metrics) {
        this(
                parser,
                processedMessageService,
                deadLetterPublisher,
                retryProperties,
                eventRepository,
                metrics,
                Thread::sleep);
    }

    RecEventPersistConsumer(
            DomainEventMessageParser parser,
            ProcessedMessageService processedMessageService,
            DeadLetterPublisher deadLetterPublisher,
            ConsumerRetryProperties retryProperties,
            RecommendationEventJdbcRepository eventRepository,
            RecommendationMetrics metrics,
            Sleeper sleeper) {
        this.parser = parser;
        this.processedMessageService = processedMessageService;
        this.deadLetterPublisher = deadLetterPublisher;
        this.retryProperties = retryProperties;
        this.eventRepository = eventRepository;
        this.metrics = metrics;
        this.sleeper = sleeper;
    }

    /**
     * Consumes a recommendation event and persists it idempotently.
     *
     * @param message delivered RabbitMQ message carrying the domain event envelope
     * @param channel channel used for manual acknowledgement and DLQ routing
     */
    @RabbitListener(queues = RabbitMqTopologyConfig.REC_EVENTS_QUEUE)
    public void consume(Message message, Channel channel) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            DomainEventEnvelope event = parser.parse(message);
            validateEnvelope(event);
            processWithRetry(event);
            ack(channel, deliveryTag);
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
                ProcessedMessageResult result =
                        processedMessageService.processOnce(
                                CONSUMER_NAME,
                                event.eventId(),
                                event.eventType(),
                                () -> apply(event));
                metrics.consumed(
                        CONSUMER_NAME,
                        result == ProcessedMessageResult.DUPLICATE ? "duplicate" : "processed");
                return;
            } catch (RuntimeException ex) {
                if (isPermanent(ex)) {
                    throw ex;
                }
                if (!isTransient(ex)) {
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

    private void apply(DomainEventEnvelope event) {
        switch (event.eventType()) {
            case RecommendationEventTypes.REC_INTERACTION_RECORDED_V1 -> {
                Map<String, Object> data = event.data();
                // The envelope timestamp is when the user acted; consumption time would skew decay
                // and training windows whenever the queue lags or a DLQ replay happens.
                OffsetDateTime occurredAt =
                        event.occurredAt() != null
                                ? event.occurredAt()
                                : OffsetDateTime.now(ZoneOffset.UTC);
                eventRepository.insertUserEvent(
                        UUID.randomUUID(),
                        event.actorId(),
                        readUuid(data, "sessionId"),
                        readString(data, "eventType"),
                        readString(data, "entityType"),
                        readUuid(data, "entityId"),
                        readString(data, "platform"),
                        occurredAt);
            }
            case RecommendationEventTypes.REC_IMPRESSION_BATCH_V1 -> {
                ImpressionBatchEvent batch = parseImpressionBatch(event.data());
                eventRepository.insertImpressions(event.actorId(), batch);
                // post_view items are behavioral interactions, not just display records; without
                // this write the collaborative filter would never see any view signal.
                eventRepository.insertPostViewUserEvents(event.actorId(), batch);
            }
            default ->
                    throw new PermanentMessageException("unknown event type: " + event.eventType());
        }
    }

    private ImpressionBatchEvent parseImpressionBatch(Map<String, Object> data) {
        UUID sessionId = readUuid(data, "sessionId");
        String platform = readString(data, "platform");
        UUID requestId = readUuid(data, "requestId");
        Object rawItems = data.get("items");
        if (!(rawItems instanceof List<?> rawList)) {
            throw new PermanentMessageException("Impression batch missing items list");
        }
        List<ImpressionBatchEvent.Item> items = new ArrayList<>();
        for (Object raw : rawList) {
            if (!(raw instanceof Map<?, ?> itemMap)) {
                throw new PermanentMessageException("Impression batch item is not an object");
            }
            items.add(
                    new ImpressionBatchEvent.Item(
                            readUuid(itemMap, "clientEventId"),
                            readString(itemMap, "type"),
                            readUuid(itemMap, "postId"),
                            readInt(itemMap, "position"),
                            readString(itemMap, "source"),
                            readOffsetDateTime(itemMap, "occurredAt")));
        }
        return new ImpressionBatchEvent(sessionId, platform, requestId, items);
    }

    private static String readString(Map<?, ?> data, String key) {
        Object value = data.get(key);
        return value == null ? null : value.toString();
    }

    private static UUID readUuid(Map<?, ?> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException ex) {
            throw new PermanentMessageException("Invalid UUID for field " + key + ": " + value);
        }
    }

    private static int readInt(Map<?, ?> data, String key) {
        Object value = data.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new PermanentMessageException("Invalid integer for field " + key + ": " + value);
    }

    private static OffsetDateTime readOffsetDateTime(Map<?, ?> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime odt) {
            return odt;
        }
        try {
            return OffsetDateTime.parse(value.toString());
        } catch (RuntimeException ex) {
            throw new PermanentMessageException(
                    "Invalid timestamp for field " + key + ": " + value);
        }
    }

    private void validateEnvelope(DomainEventEnvelope event) {
        if (event == null || event.eventId() == null) {
            throw new PermanentMessageException("Event envelope or id is null");
        }
        if (event.eventType() == null || event.eventType().isBlank()) {
            throw new PermanentMessageException("Event type is missing");
        }
        if (event.actorId() == null) {
            throw new PermanentMessageException("Event actor id is missing");
        }
    }

    private void sleepBeforeRetry(int attempt) {
        Duration backoff = retryProperties.retryBackoffForAttempt(attempt);
        if (backoff.isZero()) {
            return;
        }
        try {
            sleeper.sleep(backoff.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during recommendation event retry backoff", ex);
        }
    }

    private void routeToDlqOrRequeue(
            Message message, Channel channel, long deliveryTag, RuntimeException failure) {
        try {
            deadLetterPublisher.publish(
                    message,
                    RabbitMqTopologyConfig.REC_EVENTS_DEAD_LETTER_ROUTING_KEY,
                    failure.getMessage());
            // Counted here rather than in the retry loop so permanent failures (parse errors,
            // unknown event types) are included in the dlq outcome, not only retry exhaustion.
            metrics.consumed(CONSUMER_NAME, "dlq");
            ack(channel, deliveryTag);
        } catch (RuntimeException dlqFailure) {
            log.warn(
                    "Failed to publish recommendation event to DLQ; requeueing: {}",
                    dlqFailure.getMessage());
            nack(channel, deliveryTag);
        }
    }

    private void ack(Channel channel, long deliveryTag) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException ex) {
            log.error("Failed to ack recommendation event message: {}", ex.getMessage());
        }
    }

    private void nack(Channel channel, long deliveryTag) {
        try {
            channel.basicNack(deliveryTag, false, true);
        } catch (IOException ex) {
            log.error("Failed to nack recommendation event message: {}", ex.getMessage());
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

    boolean isTransient(Throwable ex) {
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

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
