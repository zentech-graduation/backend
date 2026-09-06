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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.app.common.config.rabbit.RabbitMqTopologyConfig;
import com.app.common.outbox.config.OutboxPublisherProperties;
import com.app.common.outbox.entity.OutboxEvent;
import com.app.common.outbox.exception.OutboxPublishException;
import com.app.common.outbox.model.DomainEventEnvelopeJson;
import com.app.common.outbox.service.OutboxPublisherService;
import com.app.common.outbox.service.OutboxPublisherStateService;

@Service
@ConditionalOnProperty(
        prefix = "app.outbox.publisher",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OutboxPublisherServiceImpl implements OutboxPublisherService {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherServiceImpl.class);
    private static final int MAX_ERROR_LENGTH = 2000;

    private final OutboxPublisherStateService outboxPublisherStateService;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxPublisherProperties properties;

    public OutboxPublisherServiceImpl(
            OutboxPublisherStateService outboxPublisherStateService,
            RabbitTemplate rabbitTemplate,
            OutboxPublisherProperties properties) {
        this.outboxPublisherStateService = outboxPublisherStateService;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Override
    @Scheduled(
            initialDelayString = "${app.outbox.publisher.initial-delay:PT10S}",
            fixedDelayString = "${app.outbox.publisher.fixed-delay:PT1S}")
    public int publishDueEvents() {
        OffsetDateTime now = now();
        List<OutboxEvent> events =
                outboxPublisherStateService.claimPublishableBatch(now, resolvedBatchSize());
        events.forEach(this::publishOne);
        return events.size();
    }

    private void publishOne(OutboxEvent event) {
        try {
            publishToRabbit(event);
        } catch (RuntimeException ex) {
            // Every async domain event in the system flows through this publisher; without this
            // log, a publish failure is only ever visible as a truncated string in the DB.
            log.error("Outbox publish failed for event {}", event.getEventId(), ex);
            recordFailure(event, ex);
            return;
        }
        boolean marked = outboxPublisherStateService.markPublished(event, now());
        if (!marked) {
            log.warn(
                    "Skipped marking outbox event {} as PUBLISHED because claim {} is no longer active",
                    event.getEventId(),
                    event.getClaimId());
        }
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
            boolean marked =
                    outboxPublisherStateService.markDead(event, nextAttempt, failedAt, lastError);
            if (!marked) {
                log.warn(
                        "Skipped marking outbox event {} as DEAD because claim {} is no longer active",
                        event.getEventId(),
                        event.getClaimId());
            }
            return;
        }
        OffsetDateTime nextRetryAt = failedAt.plus(properties.retryBackoffForAttempt(nextAttempt));
        boolean marked =
                outboxPublisherStateService.markFailed(event, nextAttempt, nextRetryAt, lastError);
        if (!marked) {
            log.warn(
                    "Skipped marking outbox event {} as PENDING because claim {} is no longer active",
                    event.getEventId(),
                    event.getClaimId());
        }
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
