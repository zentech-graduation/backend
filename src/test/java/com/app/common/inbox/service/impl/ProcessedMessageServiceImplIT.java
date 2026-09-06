package com.app.common.inbox.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.app.common.inbox.enums.ProcessedMessageResult;
import com.app.common.inbox.repository.ProcessedMessageRepository;
import com.app.common.inbox.service.ProcessedMessageService;

@DataJpaTest(
        properties = {
            "spring.docker.compose.enabled=false",
            "spring.datasource.hikari.data-source-properties.stringtype=unspecified"
        })
@Testcontainers
@Import(ProcessedMessageServiceImpl.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProcessedMessageServiceImplIT {

    private static final String CONSUMER_NAME = "mail-consumer";
    private static final String EVENT_TYPE = "user.registered.v1";

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private ProcessedMessageService processedMessageService;
    @Autowired private ProcessedMessageRepository processedMessageRepository;

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Test
    void processOnce_firstMessageProcessesAndPersistsIdempotencyRecord() {
        UUID eventId = UUID.randomUUID();
        AtomicInteger handlerRuns = new AtomicInteger();

        ProcessedMessageResult result =
                processedMessageService.processOnce(
                        CONSUMER_NAME, eventId, EVENT_TYPE, handlerRuns::incrementAndGet);

        assertThat(result).isEqualTo(ProcessedMessageResult.PROCESSED);
        assertThat(handlerRuns).hasValue(1);
        assertThat(processedMessageRepository.findByConsumerNameAndEventId(CONSUMER_NAME, eventId))
                .isPresent()
                .get()
                .satisfies(
                        message -> {
                            assertThat(message.getId()).isNotNull();
                            assertThat(message.getEventType()).isEqualTo(EVENT_TYPE);
                            assertThat(message.getProcessedAt()).isNotNull();
                        });
    }

    @Test
    void processOnce_duplicateMessageSkipsHandler() {
        UUID eventId = UUID.randomUUID();
        AtomicInteger handlerRuns = new AtomicInteger();
        processedMessageService.processOnce(
                CONSUMER_NAME, eventId, EVENT_TYPE, handlerRuns::incrementAndGet);

        ProcessedMessageResult result =
                processedMessageService.processOnce(
                        CONSUMER_NAME, eventId, EVENT_TYPE, handlerRuns::incrementAndGet);

        assertThat(result).isEqualTo(ProcessedMessageResult.DUPLICATE);
        assertThat(handlerRuns).hasValue(1);
    }

    @Test
    void processOnce_failedProcessingDoesNotCreateFalseSuccessRecord() {
        UUID eventId = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                processedMessageService.processOnce(
                                        CONSUMER_NAME,
                                        eventId,
                                        EVENT_TYPE,
                                        () -> {
                                            throw new IllegalStateException("provider failed");
                                        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provider failed");

        assertThat(processedMessageRepository.findByConsumerNameAndEventId(CONSUMER_NAME, eventId))
                .isEmpty();
    }
}
