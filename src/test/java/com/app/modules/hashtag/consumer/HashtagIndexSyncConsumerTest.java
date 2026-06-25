package com.app.modules.hashtag.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.app.common.outbox.model.DomainEventEnvelope;
import com.app.common.outbox.model.DomainEventEnvelopeJson;
import com.app.modules.hashtag.event.HashtagIndexUpsertEvent;
import com.app.modules.hashtag.messaging.HashtagEventTypes;
import com.app.modules.hashtag.repository.HashtagIndexProjection;
import com.app.modules.hashtag.repository.HashtagRepository;
import com.app.modules.hashtag.search.HashtagSearchRepository;
import com.rabbitmq.client.Channel;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class HashtagIndexSyncConsumerTest {

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID HASHTAG_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Mock private ProcessedMessageService processedMessageService;
    @Mock private DeadLetterPublisher deadLetterPublisher;
    @Mock private HashtagSearchRepository hashtagSearchRepository;
    @Mock private HashtagRepository hashtagRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private Channel channel;
    @Mock private HashtagIndexProjection projection;

    private ConsumerRetryProperties retryProperties;
    private List<Long> sleptMillis;
    private HashtagIndexSyncConsumer consumer;

    @BeforeEach
    void setUp() {
        retryProperties = new ConsumerRetryProperties();
        retryProperties.setMaxAttempts(3);
        retryProperties.setRetryBackoffs(List.of(Duration.ofMillis(5), Duration.ZERO));
        sleptMillis = new ArrayList<>();
        consumer =
                new HashtagIndexSyncConsumer(
                        new DomainEventMessageParser(),
                        processedMessageService,
                        deadLetterPublisher,
                        retryProperties,
                        hashtagSearchRepository,
                        hashtagRepository,
                        objectMapper,
                        sleptMillis::add);
    }

    @Test
    void isTransient_dataAccessException_returnsTrue() {
        assertThat(consumer.isTransient(new QueryTimeoutException("timeout"))).isTrue();
    }

    @Test
    void isTransient_redisSystemException_returnsTrue() {
        assertThat(consumer.isTransient(new RedisSystemException("redis down", null))).isTrue();
    }

    @Test
    void isTransient_amqpException_returnsTrue() {
        assertThat(consumer.isTransient(new AmqpException("broker down"))).isTrue();
    }

    @Test
    void isTransient_appExceptionServiceUnavailable_returnsTrue() {
        assertThat(consumer.isTransient(new AppException(ApiErrorCode.SERVICE_UNAVAILABLE)))
                .isTrue();
    }

    @Test
    void isTransient_appExceptionOtherCode_returnsFalse() {
        assertThat(consumer.isTransient(new AppException(ApiErrorCode.HASHTAG_NOT_FOUND)))
                .isFalse();
    }

    @Test
    void isTransient_unknownRuntimeException_returnsTrue() {
        assertThat(consumer.isTransient(new IllegalStateException("unexpected"))).isTrue();
    }

    @Test
    void consume_nullEventId_routesToDlqAndAcks() throws Exception {
        Message message = message(nullEventIdEnvelope());

        consumer.consume(message, channel);

        verify(deadLetterPublisher)
                .publish(
                        eq(message),
                        eq(RabbitMqTopologyConfig.HASHTAG_INDEX_DEAD_LETTER_ROUTING_KEY),
                        any());
        verify(channel).basicAck(1L, false);
    }

    @Test
    void consume_blankEventType_routesToDlqAndAcks() throws Exception {
        Message message = message(blankEventTypeEnvelope());

        consumer.consume(message, channel);

        verify(deadLetterPublisher)
                .publish(
                        eq(message),
                        eq(RabbitMqTopologyConfig.HASHTAG_INDEX_DEAD_LETTER_ROUTING_KEY),
                        any());
        verify(channel).basicAck(1L, false);
    }

    @Test
    void consume_unknownEventType_routesToDlqAndAcks() throws Exception {
        Message message = message(envelope("unknown.event.type"));
        when(processedMessageService.processOnce(any(), any(), any(), any()))
                .thenAnswer(
                        inv -> {
                            inv.getArgument(3, Runnable.class).run();
                            return ProcessedMessageResult.PROCESSED;
                        });

        consumer.consume(message, channel);

        verify(deadLetterPublisher)
                .publish(
                        eq(message),
                        eq(RabbitMqTopologyConfig.HASHTAG_INDEX_DEAD_LETTER_ROUTING_KEY),
                        any());
        verify(channel).basicAck(1L, false);
        verify(hashtagSearchRepository, never()).save(any());
    }

    @Test
    void consume_upsertProjectionAbsent_deletesFromIndexAndAcks() throws Exception {
        Message message = message(envelope(HashtagEventTypes.HASHTAG_INDEX_UPSERT_V1));
        when(processedMessageService.processOnce(any(), any(), any(), any()))
                .thenAnswer(
                        inv -> {
                            inv.getArgument(3, Runnable.class).run();
                            return ProcessedMessageResult.PROCESSED;
                        });
        when(objectMapper.convertValue(any(), eq(HashtagIndexUpsertEvent.class)))
                .thenReturn(upsertEvent());
        when(hashtagRepository.findIndexProjectionsByIdIn(List.of(HASHTAG_ID)))
                .thenReturn(List.of());

        consumer.consume(message, channel);

        verify(hashtagSearchRepository).deleteById(HASHTAG_ID.toString());
        verify(hashtagSearchRepository, never()).save(any());
        verify(channel).basicAck(1L, false);
    }

    @Test
    void consume_upsertPostCountZero_deletesFromIndexAndAcks() throws Exception {
        Message message = message(envelope(HashtagEventTypes.HASHTAG_INDEX_UPSERT_V1));
        when(processedMessageService.processOnce(any(), any(), any(), any()))
                .thenAnswer(
                        inv -> {
                            inv.getArgument(3, Runnable.class).run();
                            return ProcessedMessageResult.PROCESSED;
                        });
        when(objectMapper.convertValue(any(), eq(HashtagIndexUpsertEvent.class)))
                .thenReturn(upsertEvent());
        when(projection.getPostCount()).thenReturn(0);
        when(hashtagRepository.findIndexProjectionsByIdIn(List.of(HASHTAG_ID)))
                .thenReturn(List.of(projection));

        consumer.consume(message, channel);

        verify(hashtagSearchRepository).deleteById(HASHTAG_ID.toString());
        verify(hashtagSearchRepository, never()).save(any());
        verify(channel).basicAck(1L, false);
    }

    @Test
    void consume_dlqPublishFailure_nacksOriginal() throws Exception {
        Message message = message(nullEventIdEnvelope());
        doThrow(new IllegalStateException("dlq down"))
                .when(deadLetterPublisher)
                .publish(any(), any(), any());

        consumer.consume(message, channel);

        verify(channel).basicNack(1L, false, true);
        verify(channel, never()).basicAck(1L, false);
    }

    private Message message(DomainEventEnvelope envelope) {
        return MessageBuilder.withBody(
                        DomainEventEnvelopeJson.write(envelope).getBytes(StandardCharsets.UTF_8))
                .setDeliveryTag(1L)
                .build();
    }

    private DomainEventEnvelope envelope(String eventType) {
        return new DomainEventEnvelope(
                EVENT_ID,
                eventType,
                OffsetDateTime.now(ZoneOffset.UTC),
                ACTOR_ID,
                "hashtag",
                HASHTAG_ID,
                Map.of("hashtagId", HASHTAG_ID.toString()));
    }

    private DomainEventEnvelope nullEventIdEnvelope() {
        return new DomainEventEnvelope(
                null,
                HashtagEventTypes.HASHTAG_INDEX_UPSERT_V1,
                OffsetDateTime.now(ZoneOffset.UTC),
                ACTOR_ID,
                "hashtag",
                HASHTAG_ID,
                Map.of());
    }

    private DomainEventEnvelope blankEventTypeEnvelope() {
        return new DomainEventEnvelope(
                EVENT_ID,
                "",
                OffsetDateTime.now(ZoneOffset.UTC),
                ACTOR_ID,
                "hashtag",
                HASHTAG_ID,
                Map.of());
    }

    private HashtagIndexUpsertEvent upsertEvent() {
        return new HashtagIndexUpsertEvent(
                EVENT_ID, 1, OffsetDateTime.now(ZoneOffset.UTC), HASHTAG_ID);
    }
}
