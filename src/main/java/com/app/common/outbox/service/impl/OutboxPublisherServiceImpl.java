package com.app.common.outbox.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.config.rabbit.RabbitMqTopologyConfig;
import com.app.common.outbox.config.OutboxPublisherProperties;
import com.app.common.outbox.entity.OutboxEvent;
import com.app.common.outbox.exception.OutboxPublishException;
import com.app.common.outbox.model.DomainEventEnvelopeJson;
import com.app.common.outbox.repository.OutboxEventRepository;
import com.app.common.outbox.service.OutboxPublisherService;

@Service
@ConditionalOnProperty(
        prefix = "app.outbox.publisher",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OutboxPublisherServiceImpl implements OutboxPublisherService {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxPublisherProperties properties;

    public OutboxPublisherServiceImpl(
            OutboxEventRepository outboxEventRepository,
            RabbitTemplate rabbitTemplate,
            OutboxPublisherProperties properties) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Override
    // The transaction keeps selected rows locked until their broker confirm outcome is recorded.
    @Transactional
    @Scheduled(
            initialDelayString = "${app.outbox.publisher.initial-delay:PT10S}",
            fixedDelayString = "${app.outbox.publisher.fixed-delay:PT5S}")
    public int publishDueEvents() {
        OffsetDateTime now = now();
        List<OutboxEvent> events =
                outboxEventRepository.findPublishableBatch(now, resolvedBatchSize());
        events.forEach(this::publishOne);
        return events.size();
    }

    private void publishOne(OutboxEvent event) {
        try {
            publishToRabbit(event);
        } catch (RuntimeException ex) {
            recordFailure(event, ex);
            return;
        }
        outboxEventRepository.markPublished(event.getId(), now());
    }

    private void publishToRabbit(OutboxEvent event) {
        CorrelationData correlationData = new CorrelationData(event.getEventId().toString());
        rabbitTemplate.send(
                RabbitMqTopologyConfig.SOCIAL_EVENTS_EXCHANGE,
                event.getRoutingKey(),
                buildMessage(event),
                correlationData);

        CorrelationData.Confirm confirm = waitForConfirm(correlationData, event.getEventId());
        if (!confirm.ack()) {
            throw new OutboxPublishException(
                    "RabbitMQ rejected outbox event "
                            + event.getEventId()
                            + ": "
                            + confirm.reason());
        }
        if (correlationData.getReturned() != null) {
            throw new OutboxPublishException(
                    "RabbitMQ returned unroutable outbox event " + event.getEventId());
        }
    }

    private Message buildMessage(OutboxEvent event) {
        String payload = DomainEventEnvelopeJson.write(event.getPayload());
        return MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(event.getEventId().toString())
                .setHeader("eventId", event.getEventId().toString())
                .setHeader("eventType", event.getEventType())
                .setHeader("aggregateType", event.getAggregateType())
                .setHeader("aggregateId", event.getAggregateId().toString())
                .build();
    }

    private CorrelationData.Confirm waitForConfirm(CorrelationData correlationData, UUID eventId) {
        try {
            return correlationData
                    .getFuture()
                    .get(resolvedConfirmTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OutboxPublishException("Interrupted while waiting for outbox confirm", ex);
        } catch (ExecutionException ex) {
            throw new OutboxPublishException(
                    "Failed while waiting for outbox confirm " + eventId, ex.getCause());
        } catch (TimeoutException ex) {
            throw new OutboxPublishException("Timed out waiting for outbox confirm " + eventId, ex);
        }
    }

    private void recordFailure(OutboxEvent event, RuntimeException ex) {
        int nextAttempt = event.getAttemptCount() + 1;
        String lastError = truncate(ex.getMessage());
        OffsetDateTime failedAt = now();
        if (nextAttempt >= resolvedMaxAttempts()) {
            outboxEventRepository.markDead(event.getId(), nextAttempt, failedAt, lastError);
            return;
        }
        OffsetDateTime nextRetryAt = failedAt.plus(properties.retryBackoffForAttempt(nextAttempt));
        outboxEventRepository.markFailed(event.getId(), nextAttempt, nextRetryAt, lastError);
    }

    private String truncate(String message) {
        String value = message == null ? "Unknown outbox publish failure" : message;
        if (value.length() <= MAX_ERROR_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_LENGTH);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private int resolvedBatchSize() {
        return Math.max(1, properties.getBatchSize());
    }

    private int resolvedMaxAttempts() {
        return Math.max(1, properties.getMaxAttempts());
    }

    private long resolvedConfirmTimeoutMillis() {
        Duration timeout =
                properties.getConfirmTimeout() == null
                        ? Duration.ofSeconds(10)
                        : properties.getConfirmTimeout();
        return Math.max(1, timeout.toMillis());
    }
}
