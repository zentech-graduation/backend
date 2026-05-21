package com.app.common.outbox.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.app.common.outbox.entity.OutboxEvent;
import com.app.common.outbox.enums.OutboxEventStatus;
import com.app.common.outbox.model.DomainEventEnvelope;

@DataJpaTest(
        properties = {
            "spring.docker.compose.enabled=false",
            "spring.datasource.hikari.data-source-properties.stringtype=unspecified"
        })
@Testcontainers
class OutboxEventRepositoryIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private EntityManager entityManager;

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Test
    void insertPending_usesDatabaseGeneratedPrimaryKeyAndPersistsJsonPayload() {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        OffsetDateTime occurredAt = OffsetDateTime.now(ZoneOffset.UTC);
        DomainEventEnvelope payload =
                new DomainEventEnvelope(
                        eventId,
                        "user.registered.v1",
                        occurredAt,
                        aggregateId,
                        "user",
                        aggregateId,
                        Map.of("userId", aggregateId.toString()));
        OutboxEvent event =
                OutboxEvent.builder()
                        .eventId(eventId)
                        .aggregateType("user")
                        .aggregateId(aggregateId)
                        .eventType("user.registered.v1")
                        .routingKey("user.registered.v1")
                        .payload(payload)
                        .status(OutboxEventStatus.PENDING)
                        .attemptCount(0)
                        .nextRetryAt(occurredAt)
                        .build();

        OutboxEvent saved = outboxEventRepository.insertPending(event);
        entityManager.clear();
        OutboxEvent persisted = outboxEventRepository.findById(saved.getId()).orElseThrow();

        assertThat(saved.getId()).isNotNull();
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getPayload().eventId()).isEqualTo(eventId);
        assertThat(persisted.getPayload().data()).containsEntry("userId", aggregateId.toString());
    }
}
