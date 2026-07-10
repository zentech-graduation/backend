package com.app.modules.recommendation.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisSystemException;

import com.app.common.config.rabbit.RabbitMqTopologyConfig;
import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.inbox.enums.ProcessedMessageResult;
import com.app.common.inbox.service.ProcessedMessageService;
import com.app.common.messaging.DeadLetterPublisher;
import com.app.common.messaging.DomainEventMessageParser;
import com.app.common.messaging.config.ConsumerRetryProperties;
import com.app.common.messaging.exception.PermanentMessageException;
import com.app.common.outbox.model.DomainEventEnvelope;
import com.app.common.outbox.model.DomainEventEnvelopeJson;
import com.app.modules.recommendation.messaging.RecommendationEventTypes;
import com.app.modules.recommendation.observability.RecommendationMetrics;
import com.app.modules.recommendation.repository.RecommendationEventJdbcRepository;
import com.rabbitmq.client.Channel;

@ExtendWith(MockitoExtension.class)
class RecEventPersistConsumerTest {

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000789");
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000111");

    @Mock private ProcessedMessageService processedMessageService;
    @Mock private DeadLetterPublisher deadLetterPublisher;
    @Mock private RecommendationEventJdbcRepository eventRepository;
    @Mock private RecommendationMetrics metrics;
    @Mock private Channel channel;

    private DomainEventMessageParser parser;
    private ConsumerRetryProperties retryProperties;
    private List<Long> sleptMillis;
    private RecEventPersistConsumer consumer;

    @BeforeEach
    void setUp() {
        parser = new DomainEventMessageParser();
        retryProperties = new ConsumerRetryProperties();
        retryProperties.setMaxAttempts(3);
        retryProperties.setRetryBackoffs(List.of(Duration.ofMillis(5), Duration.ZERO));
        sleptMillis = new ArrayList<>();
        consumer =
                new RecEventPersistConsumer(
                        parser,
                        processedMessageService,
                        deadLetterPublisher,
                        retryProperties,
                        eventRepository,
                        metrics,
                        sleptMillis::add);
    }

    @Test
    void consumeDuplicateEvent_acksWithoutPersisting() throws Exception {
        Message message = message(interactionEvent());
        when(processedMessageService.processOnce(
                        eq(RecEventPersistConsumer.CONSUMER_NAME),
                        eq(EVENT_ID),
                        eq(RecommendationEventTypes.REC_INTERACTION_RECORDED_V1),
                        any()))
                .thenReturn(ProcessedMessageResult.DUPLICATE);

        consumer.consume(message, channel);

        verify(eventRepository, never()).insertUserEvent(any(), any(), any(), any(), any(), any(), any(), any());
        verify(channel).basicAck(1L, false);
    }

    @Test
    void consumeProcessedEvent_persistsAndAcks() throws Exception {
        Message message = message(interactionEvent());
        // processOnce must actually run the handler so the repository side effect occurs; a plain
        // thenReturn would skip the Runnable and leave insertUserEvent un-invoked.
        when(processedMessageService.processOnce(
                        eq(RecEventPersistConsumer.CONSUMER_NAME),
                        eq(EVENT_ID),
                        eq(RecommendationEventTypes.REC_INTERACTION_RECORDED_V1),
                        any()))
                .thenAnswer(
                        invocation -> {
                            invocation.getArgument(3, Runnable.class).run();
                            return ProcessedMessageResult.PROCESSED;
                        });

        consumer.consume(message, channel);

        verify(eventRepository)
                .insertUserEvent(any(), eq(ACTOR_ID), any(), eq("post_like"), any(), any(), any(), any());
        verify(channel).basicAck(1L, false);
    }

    @Test
    void consumePermanentFailure_routesToDlqAndAcks() throws Exception {
        Message message = message(interactionEvent());
        when(processedMessageService.processOnce(any(), any(), any(), any()))
                .thenThrow(new PermanentMessageException("bad payload"));

        consumer.consume(message, channel);

        verify(deadLetterPublisher)
                .publish(
                        eq(message),
                        eq(RabbitMqTopologyConfig.REC_EVENTS_DEAD_LETTER_ROUTING_KEY),
                        eq("bad payload"));
        verify(channel).basicAck(1L, false);
        verify(channel, never()).basicNack(1L, false, true);
    }

    @Test
    void consumeDlqPublishFails_nacksWithRequeue() throws Exception {
        Message message = message(interactionEvent());
        when(processedMessageService.processOnce(any(), any(), any(), any()))
                .thenThrow(new PermanentMessageException("bad payload"));
        doThrow(new IllegalStateException("dlq down"))
                .when(deadLetterPublisher)
                .publish(message, RabbitMqTopologyConfig.REC_EVENTS_DEAD_LETTER_ROUTING_KEY, "bad payload");

        consumer.consume(message, channel);

        verify(channel).basicNack(1L, false, true);
    }

    @Test
    void consumeTransientFailure_retriesThenRoutesToDlq() throws Exception {
        Message message = message(interactionEvent());
        when(processedMessageService.processOnce(any(), any(), any(), any()))
                .thenThrow(new QueryTimeoutException("db timeout"));

        consumer.consume(message, channel);

        // maxAttempts = 3, so processOnce is invoked 3 times before giving up and routing to DLQ.
        verify(processedMessageService, org.mockito.Mockito.times(3)).processOnce(any(), any(), any(), any());
        verify(deadLetterPublisher)
                .publish(
                        eq(message),
                        eq(RabbitMqTopologyConfig.REC_EVENTS_DEAD_LETTER_ROUTING_KEY),
                        eq("db timeout"));
        verify(channel).basicAck(1L, false);
        // Attempt 1 sleeps 5ms; attempt 2 has a zero backoff so sleepBeforeRetry returns early
        // without invoking the sleeper; attempt 3 breaks out of the loop. Net: one sleep.
        assertThat(sleptMillis).hasSize(1);
    }

    @Test
    void consumeUnknownEventType_routesToDlq() throws Exception {
        Message message = message(event("rec.unknown.v1", Map.of()));
        when(processedMessageService.processOnce(any(), any(), any(), any()))
                .thenThrow(new PermanentMessageException("unknown event type: rec.unknown.v1"));

        consumer.consume(message, channel);

        verify(deadLetterPublisher)
                .publish(
                        eq(message),
                        eq(RabbitMqTopologyConfig.REC_EVENTS_DEAD_LETTER_ROUTING_KEY),
                        eq("unknown event type: rec.unknown.v1"));
        verify(channel).basicAck(1L, false);
    }

    @Test
    void consume_ackThrowsIOException_doesNotPropagate() throws Exception {
        Message message = message(interactionEvent());
        when(processedMessageService.processOnce(any(), any(), any(), any()))
                .thenReturn(ProcessedMessageResult.PROCESSED);
        doThrow(new IOException("channel closed")).when(channel).basicAck(1L, false);

        assertThatCode(() -> consumer.consume(message, channel)).doesNotThrowAnyException();
    }

    @Test
    void isTransient_dataAccessException_returnsTrue() {
        assertThat(consumer.isTransient(new QueryTimeoutException("db timeout"))).isTrue();
    }

    @Test
    void isTransient_redisSystemException_returnsTrue() {
        assertThat(consumer.isTransient(new RedisSystemException("redis down", null))).isTrue();
    }

    @Test
    void isTransient_amqpException_returnsTrue() {
        assertThat(consumer.isTransient(new AmqpException("broker unreachable"))).isTrue();
    }

    @Test
    void isTransient_appExceptionNonServiceUnavailable_returnsFalse() {
        assertThat(consumer.isTransient(new AppException(ApiErrorCode.POST_NOT_FOUND))).isFalse();
    }

    private Message message(DomainEventEnvelope event) {
        return MessageBuilder.withBody(
                        DomainEventEnvelopeJson.write(event).getBytes(StandardCharsets.UTF_8))
                .setDeliveryTag(1L)
                .build();
    }

    private DomainEventEnvelope interactionEvent() {
        UUID postId = UUID.fromString("00000000-0000-0000-0000-000000000222");
        return event(
                RecommendationEventTypes.REC_INTERACTION_RECORDED_V1,
                Map.of(
                        "eventType", "post_like",
                        "entityType", "post",
                        "entityId", postId.toString(),
                        "targetUserId", ACTOR_ID.toString()));
    }

    private DomainEventEnvelope event(String eventType, Map<String, Object> data) {
        return new DomainEventEnvelope(
                EVENT_ID,
                eventType,
                OffsetDateTime.now(ZoneOffset.UTC),
                ACTOR_ID,
                "user",
                ACTOR_ID,
                data);
    }
}
