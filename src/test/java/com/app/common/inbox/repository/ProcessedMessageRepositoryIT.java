package com.app.common.inbox.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

@DataJpaTest(
        properties = {
            "spring.docker.compose.enabled=false",
            "spring.datasource.hikari.data-source-properties.stringtype=unspecified"
        })
@Testcontainers
class ProcessedMessageRepositoryIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private ProcessedMessageRepository processedMessageRepository;
    @Autowired private EntityManager entityManager;

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Test
    void deleteProcessedBefore_removesOnlyRecordsProcessedBeforeTheCutoff() {
        UUID oldEventId = insertProcessed("old-consumer");
        UUID recentEventId = insertProcessed("recent-consumer");
        backdate(oldEventId, 40);

        int deleted = processedMessageRepository.deleteProcessedBefore(daysAgo(30), 500);
        entityManager.clear();

        assertThat(deleted).isEqualTo(1);
        assertThat(
                        processedMessageRepository.findByConsumerNameAndEventId(
                                "old-consumer", oldEventId))
                .isEmpty();
        assertThat(
                        processedMessageRepository.findByConsumerNameAndEventId(
                                "recent-consumer", recentEventId))
                .isPresent();
    }

    @Test
    void deleteProcessedBefore_recentGuardsSurvive_soARedeliveredMessageStaysSuppressed() {
        UUID eventId = insertProcessed("consumer");

        int deleted = processedMessageRepository.deleteProcessedBefore(daysAgo(30), 500);
        entityManager.clear();

        // The guard for a just-processed event must outlive every redelivery window, otherwise the
        // next delivery of that event re-runs the consumer's side effects.
        assertThat(deleted).isZero();
        assertThat(processedMessageRepository.findByConsumerNameAndEventId("consumer", eventId))
                .isPresent();
    }

    @Test
    void deleteProcessedBefore_honoursBatchSize() {
        for (int i = 0; i < 5; i++) {
            backdate(insertProcessed("consumer-" + i), 40);
        }

        int firstBatch = processedMessageRepository.deleteProcessedBefore(daysAgo(30), 2);
        int secondBatch = processedMessageRepository.deleteProcessedBefore(daysAgo(30), 2);
        int lastBatch = processedMessageRepository.deleteProcessedBefore(daysAgo(30), 2);

        assertThat(firstBatch).isEqualTo(2);
        assertThat(secondBatch).isEqualTo(2);
        assertThat(lastBatch).isEqualTo(1);
    }

    private UUID insertProcessed(String consumerName) {
        UUID eventId = UUID.randomUUID();
        processedMessageRepository.insertIfAbsent(consumerName, eventId, "user.registered.v1");
        return eventId;
    }

    private void backdate(UUID eventId, int days) {
        entityManager
                .createNativeQuery(
                        "UPDATE processed_messages SET processed_at = NOW() - CAST(:days AS"
                                + " interval) WHERE event_id = :eventId")
                .setParameter("days", days + " days")
                .setParameter("eventId", eventId)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    private OffsetDateTime daysAgo(int days) {
        return OffsetDateTime.now(ZoneOffset.UTC).minusDays(days);
    }
}
