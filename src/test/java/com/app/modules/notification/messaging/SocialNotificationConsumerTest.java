package com.app.modules.notification.messaging;

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
import com.app.modules.notification.entity.enums.NotificationType;
import com.app.modules.notification.service.NotificationService;
import com.app.modules.social.messaging.SocialEventTypes;
import com.rabbitmq.client.Channel;

@ExtendWith(MockitoExtension.class)
class SocialNotificationConsumerTest {

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID RECIPIENT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Mock private ProcessedMessageService processedMessageService;
    @Mock private NotificationService notificationService;
    @Mock private DeadLetterPublisher deadLetterPublisher;
    @Mock private Channel channel;

    private ConsumerRetryProperties retryProperties;
    private List<Long> sleptMillis;
    private SocialNotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        retryProperties = new ConsumerRetryProperties();
        retryProperties.setMaxAttempts(3);
        retryProperties.setRetryBackoffs(List.of(Duration.ofMillis(5), Duration.ZERO));
        sleptMillis = new ArrayList<>();
        consumer =
                new SocialNotificationConsumer(
                        new DomainEventMessageParser(),
                        processedMessageService,
                        notificationService,
                        retryProperties,
                        deadLetterPublisher,
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
        assertThat(consumer.isTransient(new AppException(ApiErrorCode.NOT_FOUND))).isFalse();
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
                        eq(RabbitMqTopologyConfig.NOTIFICATION_DEAD_LETTER_ROUTING_KEY),
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
                        eq(RabbitMqTopologyConfig.NOTIFICATION_DEAD_LETTER_ROUTING_KEY),
                        any());
        verify(channel).basicAck(1L, false);
    }

    @Test
    void consume_nullAggregateId_routesToDlqAndAcks() throws Exception {
        DomainEventEnvelope envelope =
                new DomainEventEnvelope(
                        EVENT_ID,
                        SocialEventTypes.USER_FOLLOWED_V1,
                        OffsetDateTime.now(ZoneOffset.UTC),
                        ACTOR_ID,
                        "user",
                        null,
                        Map.of());
        Message message = message(envelope);

        consumer.consume(message, channel);

        verify(deadLetterPublisher)
                .publish(
                        eq(message),
                        eq(RabbitMqTopologyConfig.NOTIFICATION_DEAD_LETTER_ROUTING_KEY),
                        any());
        verify(channel).basicAck(1L, false);
    }

    @Test
    void consume_unknownEventType_acksWithoutCreatingNotification() throws Exception {
        Message message = message(envelope("unknown.event.type"));

        consumer.consume(message, channel);

        verify(notificationService, never()).create(any(), any(), any(), any(), any());
        verify(channel).basicAck(1L, false);
        verify(deadLetterPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void consume_userFollowedEvent_createsFollowNotification() throws Exception {
        Message message = message(envelope(SocialEventTypes.USER_FOLLOWED_V1));
        when(processedMessageService.processOnce(any(), any(), any(), any()))
                .thenAnswer(
                        inv -> {
                            inv.getArgument(3, Runnable.class).run();
                            return ProcessedMessageResult.PROCESSED;
                        });

        consumer.consume(message, channel);

        verify(notificationService)
                .create(ACTOR_ID, RECIPIENT_ID, NotificationType.FOLLOW, null, null);
        verify(channel).basicAck(1L, false);
    }

    @Test
    void consume_userFollowRequestedEvent_createsFollowRequestNotification() throws Exception {
        Message message = message(envelope(SocialEventTypes.USER_FOLLOW_REQUESTED_V1));
        when(processedMessageService.processOnce(any(), any(), any(), any()))
                .thenAnswer(
                        inv -> {
                            inv.getArgument(3, Runnable.class).run();
                            return ProcessedMessageResult.PROCESSED;
                        });

        consumer.consume(message, channel);

        verify(notificationService)
                .create(ACTOR_ID, RECIPIENT_ID, NotificationType.FOLLOW_REQUEST, null, null);
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
                "user",
                RECIPIENT_ID,
                Map.of());
    }

    private DomainEventEnvelope nullEventIdEnvelope() {
        return new DomainEventEnvelope(
                null,
                SocialEventTypes.USER_FOLLOWED_V1,
                OffsetDateTime.now(ZoneOffset.UTC),
                ACTOR_ID,
                "user",
                RECIPIENT_ID,
                Map.of());
    }

    private DomainEventEnvelope blankEventTypeEnvelope() {
        return new DomainEventEnvelope(
                EVENT_ID,
                "",
                OffsetDateTime.now(ZoneOffset.UTC),
                ACTOR_ID,
                "user",
                RECIPIENT_ID,
                Map.of());
    }
}
