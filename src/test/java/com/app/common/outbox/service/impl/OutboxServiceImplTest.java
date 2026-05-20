package com.app.common.outbox.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitOperations;

import com.app.common.outbox.entity.OutboxEvent;
import com.app.common.outbox.enums.OutboxEventStatus;
import com.app.common.outbox.repository.OutboxEventRepository;

@ExtendWith(MockitoExtension.class)
class OutboxServiceImplTest {

    @Mock private OutboxEventRepository outboxEventRepository;

    @Test
    void enqueueStoresRoutingKeyEventTypeAggregateIdsAndPayload() {
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        OutboxServiceImpl service = new OutboxServiceImpl(outboxEventRepository);
        UUID aggregateId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Map<String, Object> data = Map.of("postId", UUID.randomUUID().toString());

        OutboxEvent result =
                service.enqueue(
                        "comment.created.v1",
                        "comment.created.v1",
                        "comment",
                        aggregateId,
                        actorId,
                        data);

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        OutboxEvent event = eventCaptor.getValue();
        assertThat(result).isSameAs(event);
        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getEventType()).isEqualTo("comment.created.v1");
        assertThat(event.getRoutingKey()).isEqualTo("comment.created.v1");
        assertThat(event.getAggregateType()).isEqualTo("comment");
        assertThat(event.getAggregateId()).isEqualTo(aggregateId);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getAttemptCount()).isZero();
        assertThat(event.getNextRetryAt()).isNotNull();
        assertThat(event.getPayload().eventId()).isEqualTo(event.getEventId());
        assertThat(event.getPayload().eventType()).isEqualTo(event.getEventType());
        assertThat(event.getPayload().actorId()).isEqualTo(actorId);
        assertThat(event.getPayload().aggregateType()).isEqualTo("comment");
        assertThat(event.getPayload().aggregateId()).isEqualTo(aggregateId);
        assertThat(event.getPayload().data()).containsEntry("postId", data.get("postId"));
    }

    @Test
    void enqueueUsesEmptyPayloadWhenDataIsNull() {
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        OutboxServiceImpl service = new OutboxServiceImpl(outboxEventRepository);

        OutboxEvent event =
                service.enqueue(
                        "post.created.v1",
                        "post.created.v1",
                        "post",
                        UUID.randomUUID(),
                        null,
                        null);

        assertThat(event.getPayload().data()).isEmpty();
    }

    @Test
    void enqueueRejectsMissingRequiredFields() {
        OutboxServiceImpl service = new OutboxServiceImpl(outboxEventRepository);

        assertThatThrownBy(
                        () ->
                                service.enqueue(
                                        "",
                                        "post.created.v1",
                                        "post",
                                        UUID.randomUUID(),
                                        null,
                                        Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventType");
    }

    @Test
    void enqueueRejectsSensitiveDataKeys() {
        OutboxServiceImpl service = new OutboxServiceImpl(outboxEventRepository);

        assertThatThrownBy(
                        () ->
                                service.enqueue(
                                        "auth.password-reset.requested.v1",
                                        "auth.password-reset.requested.v1",
                                        "user",
                                        UUID.randomUUID(),
                                        null,
                                        Map.of("resetToken", "raw-token")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain token");
    }

    @Test
    void serviceDoesNotDependOnRabbitMqPublishing() {
        for (Field field : OutboxServiceImpl.class.getDeclaredFields()) {
            assertThat(RabbitOperations.class.isAssignableFrom(field.getType()))
                    .as("Outbox core must not publish directly through %s", field.getName())
                    .isFalse();
        }
    }
}
