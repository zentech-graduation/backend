package com.app.common.outbox.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.config.rabbit.RabbitMqTopologyConfig;
import com.app.common.outbox.config.OutboxPublisherProperties;
import com.app.common.outbox.entity.OutboxEvent;
import com.app.common.outbox.enums.OutboxEventStatus;
import com.app.common.outbox.model.DomainEventEnvelope;
import com.app.common.outbox.service.OutboxPublisherStateService;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherServiceImplTest {

    @Mock private OutboxPublisherStateService outboxPublisherStateService;
    @Mock private RabbitTemplate rabbitTemplate;

    private OutboxPublisherProperties properties;
    private OutboxPublisherServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new OutboxPublisherProperties();
        properties.setConfirmTimeout(Duration.ofMillis(20));
        service =
                new OutboxPublisherServiceImpl(
                        outboxPublisherStateService, rabbitTemplate, properties);
    }

    @Test
    void publishDueEvents_confirmSuccessMarksPublished() {
        OutboxEvent event = outboxEvent(0);
        when(outboxPublisherStateService.claimPublishableBatch(any(OffsetDateTime.class), eq(100)))
                .thenReturn(List.of(event));
        when(outboxPublisherStateService.markPublished(eq(event), any(OffsetDateTime.class)))
                .thenReturn(true);
        completeConfirm(true, null);

        int attempted = service.publishDueEvents();

        assertThat(attempted).isOne();
        verify(rabbitTemplate)
                .send(
                        eq(RabbitMqTopologyConfig.SOCIAL_EVENTS_EXCHANGE),
                        eq(event.getRoutingKey()),
                        any(Message.class),
                        any(CorrelationData.class));
        verify(outboxPublisherStateService).markPublished(eq(event), any(OffsetDateTime.class));
        verify(outboxPublisherStateService, never())
                .markFailed(any(OutboxEvent.class), anyInt(), any(OffsetDateTime.class), any());
        verify(outboxPublisherStateService, never())
                .markDead(any(OutboxEvent.class), anyInt(), any(OffsetDateTime.class), any());
    }

    @Test
    void publishDueEvents_publishFailureSchedulesRetry() {
        OutboxEvent event = outboxEvent(0);
        when(outboxPublisherStateService.claimPublishableBatch(any(OffsetDateTime.class), eq(100)))
                .thenReturn(List.of(event));
        when(outboxPublisherStateService.markFailed(
                        eq(event), eq(1), any(OffsetDateTime.class), any()))
                .thenReturn(true);
        doThrow(new AmqpException("broker unavailable"))
                .when(rabbitTemplate)
                .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        service.publishDueEvents();

        ArgumentCaptor<OffsetDateTime> nextRetryCaptor =
                ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(outboxPublisherStateService)
                .markFailed(eq(event), eq(1), nextRetryCaptor.capture(), any());
        assertThat(nextRetryCaptor.getValue()).isAfter(OffsetDateTime.now(ZoneOffset.UTC));
        verify(outboxPublisherStateService, never()).markPublished(any(), any());
        verify(outboxPublisherStateService, never()).markDead(any(), anyInt(), any(), any());
    }

    @Test
    void publishDueEvents_maxAttemptsMarksDead() {
        OutboxEvent event = outboxEvent(2);
        when(outboxPublisherStateService.claimPublishableBatch(any(OffsetDateTime.class), eq(100)))
                .thenReturn(List.of(event));
        when(outboxPublisherStateService.markDead(
                        eq(event), eq(3), any(OffsetDateTime.class), any()))
                .thenReturn(true);
        doThrow(new AmqpException("broker unavailable"))
                .when(rabbitTemplate)
                .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        service.publishDueEvents();

        verify(outboxPublisherStateService)
                .markDead(eq(event), eq(3), any(OffsetDateTime.class), any());
        verify(outboxPublisherStateService, never()).markPublished(any(), any());
        verify(outboxPublisherStateService, never())
                .markFailed(any(OutboxEvent.class), anyInt(), any(OffsetDateTime.class), any());
    }

    @Test
    void publishDueEvents_nackDoesNotMarkPublished() {
        OutboxEvent event = outboxEvent(0);
        when(outboxPublisherStateService.claimPublishableBatch(any(OffsetDateTime.class), eq(100)))
                .thenReturn(List.of(event));
        when(outboxPublisherStateService.markFailed(
                        eq(event), eq(1), any(OffsetDateTime.class), any()))
                .thenReturn(true);
        completeConfirm(false, "nack");

        service.publishDueEvents();

        verify(outboxPublisherStateService).markFailed(eq(event), eq(1), any(), any());
        verify(outboxPublisherStateService, never()).markPublished(any(), any());
    }

    @Test
    void publishDueEvents_markPublishedFailureDoesNotRollbackPreviousEventState() {
        OutboxEvent first = outboxEvent(0);
        OutboxEvent second = outboxEvent(0);
        when(outboxPublisherStateService.claimPublishableBatch(any(OffsetDateTime.class), eq(100)))
                .thenReturn(List.of(first, second));
        completeConfirm(true, null);
        doThrow(new IllegalStateException("database unavailable"))
                .when(outboxPublisherStateService)
                .markPublished(eq(second), any(OffsetDateTime.class));
        when(outboxPublisherStateService.markPublished(eq(first), any(OffsetDateTime.class)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.publishDueEvents())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database unavailable");

        verify(outboxPublisherStateService).markPublished(eq(first), any(OffsetDateTime.class));
        verify(outboxPublisherStateService).markPublished(eq(second), any(OffsetDateTime.class));
        verify(outboxPublisherStateService, never()).markFailed(any(), anyInt(), any(), any());
        verify(outboxPublisherStateService, never()).markDead(any(), anyInt(), any(), any());
    }

    @Test
    void publishDueEvents_timeoutDoesNotMarkPublishedBeforeConfirm() {
        OutboxEvent event = outboxEvent(0);
        properties.setConfirmTimeout(Duration.ofMillis(1));
        when(outboxPublisherStateService.claimPublishableBatch(any(OffsetDateTime.class), eq(100)))
                .thenReturn(List.of(event));
        when(outboxPublisherStateService.markFailed(
                        eq(event), eq(1), any(OffsetDateTime.class), any()))
                .thenReturn(true);

        service.publishDueEvents();

        verify(outboxPublisherStateService).markFailed(eq(event), eq(1), any(), any());
        verify(outboxPublisherStateService, never()).markPublished(any(), any());
    }

    @Test
    void publishDueEvents_isNotTransactional() throws NoSuchMethodException {
        Method method = OutboxPublisherServiceImpl.class.getMethod("publishDueEvents");

        assertThat(method.getAnnotation(Transactional.class)).isNull();
    }

    private void completeConfirm(boolean ack, String reason) {
        doAnswer(
                        invocation -> {
                            CorrelationData correlationData = invocation.getArgument(3);
                            correlationData
                                    .getFuture()
                                    .complete(new CorrelationData.Confirm(ack, reason));
                            return null;
                        })
                .when(rabbitTemplate)
                .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    private OutboxEvent outboxEvent(int attemptCount) {
        UUID id = UUID.randomUUID();
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
                .id(id)
                .eventId(eventId)
                .claimId(UUID.randomUUID())
                .aggregateType("user")
                .aggregateId(aggregateId)
                .eventType("user.registered.v1")
                .routingKey("user.registered.v1")
                .payload(payload)
                .status(OutboxEventStatus.PENDING)
                .attemptCount(attemptCount)
                .nextRetryAt(occurredAt)
                .createdAt(occurredAt)
                .build();
    }
}
