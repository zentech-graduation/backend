package com.app.common.outbox.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** Business event contract stored in the outbox payload and delivered through RabbitMQ. */
public record DomainEventEnvelope(
        UUID eventId,
        String eventType,
        OffsetDateTime occurredAt,
        UUID actorId,
        String aggregateType,
        UUID aggregateId,
        Map<String, Object> data) {}
