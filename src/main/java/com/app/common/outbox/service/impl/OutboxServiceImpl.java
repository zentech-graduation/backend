package com.app.common.outbox.service.impl;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.app.common.outbox.entity.OutboxEvent;
import com.app.common.outbox.enums.OutboxEventStatus;
import com.app.common.outbox.model.DomainEventEnvelope;
import com.app.common.outbox.repository.OutboxEventRepository;
import com.app.common.outbox.service.OutboxService;

@Service
public class OutboxServiceImpl implements OutboxService {

    private static final String SENSITIVE_DATA_KEY_MESSAGE =
            "Outbox event data must not contain token, password, secret, or credential fields";

    private final OutboxEventRepository outboxEventRepository;

    public OutboxServiceImpl(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Override
    // Enqueue must join the business transaction so DB writes and event intent commit together.
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent enqueue(
            String eventType,
            String routingKey,
            String aggregateType,
            UUID aggregateId,
            UUID actorId,
            Map<String, Object> data) {
        Assert.hasText(eventType, "eventType must not be blank");
        Assert.hasText(routingKey, "routingKey must not be blank");
        Assert.hasText(aggregateType, "aggregateType must not be blank");
        Assert.notNull(aggregateId, "aggregateId must not be null");

        UUID eventId = UUID.randomUUID();
        OffsetDateTime occurredAt = OffsetDateTime.now(ZoneOffset.UTC);
        Map<String, Object> eventData = data == null ? Map.of() : Map.copyOf(data);
        validateNoSensitiveDataKeys(eventData);
        DomainEventEnvelope envelope =
                new DomainEventEnvelope(
                        eventId,
                        eventType,
                        occurredAt,
                        actorId,
                        aggregateType,
                        aggregateId,
                        eventData);

        OutboxEvent event =
                OutboxEvent.builder()
                        .eventId(eventId)
                        .aggregateType(aggregateType)
                        .aggregateId(aggregateId)
                        .eventType(eventType)
                        .routingKey(routingKey)
                        .payload(envelope)
                        .status(OutboxEventStatus.PENDING)
                        .attemptCount(0)
                        .nextRetryAt(occurredAt)
                        .build();
        return outboxEventRepository.save(event);
    }

    private void validateNoSensitiveDataKeys(Map<String, Object> data) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey().toLowerCase();
            Assert.isTrue(!isSensitiveKey(key), SENSITIVE_DATA_KEY_MESSAGE);
            if (entry.getValue() instanceof Map<?, ?> nestedData) {
                validateNoSensitiveDataKeys(castNestedData(nestedData));
            }
        }
    }

    private boolean isSensitiveKey(String key) {
        return key.contains("token")
                || key.contains("password")
                || key.contains("secret")
                || key.contains("credential");
    }

    private Map<String, Object> castNestedData(Map<?, ?> data) {
        for (Object key : data.keySet()) {
            Assert.isInstanceOf(String.class, key, "Outbox event data keys must be strings");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typedData = (Map<String, Object>) data;
        return typedData;
    }
}
