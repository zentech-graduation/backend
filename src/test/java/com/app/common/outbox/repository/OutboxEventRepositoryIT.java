package com.app.common.outbox.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
        OutboxEvent event = newEvent(OffsetDateTime.now(ZoneOffset.UTC), 0);

        OutboxEvent saved = outboxEventRepository.insertPending(event);
        entityManager.clear();
        OutboxEvent persisted = outboxEventRepository.findById(saved.getId()).orElseThrow();

        assertThat(saved.getId()).isNotNull();
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getPayload().eventId()).isEqualTo(event.getEventId());
        assertThat(persisted.getPayload().data())
                .containsEntry("userId", event.getAggregateId().toString());
    }

    @Test
    void findPublishableBatch_returnsOnlyDuePendingEvents() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OutboxEvent due = outboxEventRepository.insertPending(newEvent(now.minusSeconds(1), 0));
        outboxEventRepository.insertPending(newEvent(now.plusMinutes(5), 0));

        List<OutboxEvent> events = outboxEventRepository.findPublishableBatch(now, 100);

        assertThat(events).extracting(OutboxEvent::getId).containsExactly(due.getId());
    }

    @Test
    void markPublished_setsPublishedStatusAndTimestamp() {
        OutboxEvent event =
                outboxEventRepository.insertPending(
                        newEvent(OffsetDateTime.now(ZoneOffset.UTC), 0));
        OffsetDateTime publishedAt = OffsetDateTime.now(ZoneOffset.UTC);

        outboxEventRepository.markPublished(event.getId(), publishedAt);
        entityManager.clear();
        OutboxEvent persisted = outboxEventRepository.findById(event.getId()).orElseThrow();

        assertThat(persisted.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(persisted.getPublishedAt()).isNotNull();
        assertThat(persisted.getLastError()).isNull();
    }

    @Test
    void markFailed_schedulesRetryAndStoresLastError() {
        OutboxEvent event =
                outboxEventRepository.insertPending(
                        newEvent(OffsetDateTime.now(ZoneOffset.UTC), 0));
        OffsetDateTime nextRetryAt = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(10);

        outboxEventRepository.markFailed(event.getId(), 1, nextRetryAt, "broker unavailable");
        entityManager.clear();
        OutboxEvent persisted = outboxEventRepository.findById(event.getId()).orElseThrow();

        assertThat(persisted.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(persisted.getAttemptCount()).isEqualTo(1);
        assertThat(persisted.getNextRetryAt())
                .isAfter(nextRetryAt.minusSeconds(1))
                .isBefore(nextRetryAt.plusSeconds(1));
        assertThat(persisted.getLastError()).isEqualTo("broker unavailable");
        assertThat(persisted.getPublishedAt()).isNull();
    }

    @Test
    void markDead_setsDeadStatusAndStoresLastError() {
        OutboxEvent event =
                outboxEventRepository.insertPending(
                        newEvent(OffsetDateTime.now(ZoneOffset.UTC), 2));
        OffsetDateTime deadAt = OffsetDateTime.now(ZoneOffset.UTC);

        outboxEventRepository.markDead(event.getId(), 3, deadAt, "nack");
        entityManager.clear();
        OutboxEvent persisted = outboxEventRepository.findById(event.getId()).orElseThrow();

        assertThat(persisted.getStatus()).isEqualTo(OutboxEventStatus.DEAD);
        assertThat(persisted.getAttemptCount()).isEqualTo(3);
        assertThat(persisted.getNextRetryAt())
                .isAfter(deadAt.minusSeconds(1))
                .isBefore(deadAt.plusSeconds(1));
        assertThat(persisted.getLastError()).isEqualTo("nack");
        assertThat(persisted.getPublishedAt()).isNull();
    }

    private OutboxEvent newEvent(OffsetDateTime nextRetryAt, int attemptCount) {
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
        return OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType("user")
                .aggregateId(aggregateId)
                .eventType("user.registered.v1")
                .routingKey("user.registered.v1")
                .payload(payload)
                .status(OutboxEventStatus.PENDING)
                .attemptCount(attemptCount)
                .nextRetryAt(nextRetryAt)
                .build();
    }
}
