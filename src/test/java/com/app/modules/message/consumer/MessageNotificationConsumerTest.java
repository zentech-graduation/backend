package com.app.modules.message.consumer;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.dao.QueryTimeoutException;

import com.app.common.config.rabbit.RabbitMqTopologyConfig;
import com.app.common.inbox.enums.ProcessedMessageResult;
import com.app.common.inbox.service.ProcessedMessageService;
import com.app.common.messaging.DeadLetterPublisher;
import com.app.common.messaging.DomainEventMessageParser;
import com.app.common.messaging.config.ConsumerRetryProperties;
import com.app.common.outbox.model.DomainEventEnvelope;
import com.app.common.outbox.model.DomainEventEnvelopeJson;
import com.app.modules.message.messaging.MessageEventTypes;
import com.app.modules.message.repository.ConversationParticipantRepository;
import com.app.modules.notification.entity.enums.NotificationType;
import com.app.modules.notification.service.NotificationService;
import com.rabbitmq.client.Channel;

@ExtendWith(MockitoExtension.class)
class MessageNotificationConsumerTest {

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SENDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CONVERSATION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID RECIPIENT1_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID RECIPIENT2_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000006");

    @Mock private ProcessedMessageService processedMessageService;
    @Mock private ConversationParticipantRepository participantRepository;
    @Mock private NotificationService notificationService;
    @Mock private DeadLetterPublisher deadLetterPublisher;
    @Mock private Channel channel;

    private MessageNotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        // maxAttempts=1 with a zero backoff means any transient failure "exhausts" retry on the
        // first attempt without a real Thread.sleep, keeping this test fast and deterministic.
        ConsumerRetryProperties retryProperties = new ConsumerRetryProperties();
        retryProperties.setMaxAttempts(1);
        retryProperties.setRetryBackoffs(List.of(Duration.ZERO));
        consumer =
                new MessageNotificationConsumer(
                        new DomainEventMessageParser(),
                        processedMessageService,
                        participantRepository,
                        notificationService,
                        retryProperties,
                        deadLetterPublisher);
    }

    @Test
    void consume_messageSent_notifiesActiveParticipantsExceptSender() throws Exception {
        Message message = message(envelope());
        when(processedMessageService.processOnce(any(), any(), any(), any()))
                .thenAnswer(
                        inv -> {
                            inv.getArgument(3, Runnable.class).run();
                            return ProcessedMessageResult.PROCESSED;
                        });
        when(participantRepository.findActiveUserIdsByConversationId(CONVERSATION_ID))
                .thenReturn(List.of(SENDER_ID, RECIPIENT1_ID, RECIPIENT2_ID));

        consumer.consume(message, channel);

        verify(notificationService)
                .create(SENDER_ID, RECIPIENT1_ID, NotificationType.MESSAGE, "message", MESSAGE_ID);
        verify(notificationService)
                .create(SENDER_ID, RECIPIENT2_ID, NotificationType.MESSAGE, "message", MESSAGE_ID);
        verify(notificationService, never())
                .create(eq(SENDER_ID), eq(SENDER_ID), any(), any(), any());
        verify(channel).basicAck(1L, false);
        verify(deadLetterPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void consume_duplicateEvent_skipsHandlerAndAcks() throws Exception {
        Message message = message(envelope());
        when(processedMessageService.processOnce(any(), any(), any(), any()))
                .thenReturn(ProcessedMessageResult.DUPLICATE);

        consumer.consume(message, channel);

        verify(notificationService, never()).create(any(), any(), any(), any(), any());
        verify(channel).basicAck(1L, false);
    }

    @Test
    void consume_missingConversationId_routesToDlqAndAcks() throws Exception {
        Message message =
                message(
                        new DomainEventEnvelope(
                                EVENT_ID,
                                MessageEventTypes.MESSAGE_SENT_V1,
                                OffsetDateTime.now(ZoneOffset.UTC),
                                SENDER_ID,
                                "message",
                                MESSAGE_ID,
                                Map.of("messageId", MESSAGE_ID.toString())));

        consumer.consume(message, channel);

        verify(deadLetterPublisher)
                .publish(
                        eq(message),
                        eq(RabbitMqTopologyConfig.MESSAGE_NOTIFICATION_DEAD_LETTER_ROUTING_KEY),
                        any());
        verify(channel).basicAck(1L, false);
        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    void consume_missingMessageId_routesToDlqAndAcks() throws Exception {
        Message message =
                message(
                        new DomainEventEnvelope(
                                EVENT_ID,
                                MessageEventTypes.MESSAGE_SENT_V1,
                                OffsetDateTime.now(ZoneOffset.UTC),
                                SENDER_ID,
                                "message",
                                MESSAGE_ID,
                                Map.of("conversationId", CONVERSATION_ID.toString())));

        consumer.consume(message, channel);

        verify(deadLetterPublisher)
                .publish(
                        eq(message),
                        eq(RabbitMqTopologyConfig.MESSAGE_NOTIFICATION_DEAD_LETTER_ROUTING_KEY),
                        any());
        verify(channel).basicAck(1L, false);
    }

    @Test
    void consume_transientFailureExhaustsRetry_routesToDlqAndAcks() throws Exception {
        Message message = message(envelope());
        when(processedMessageService.processOnce(any(), any(), any(), any()))
                .thenThrow(new QueryTimeoutException("db down"));

        consumer.consume(message, channel);

        verify(deadLetterPublisher)
                .publish(
                        eq(message),
                        eq(RabbitMqTopologyConfig.MESSAGE_NOTIFICATION_DEAD_LETTER_ROUTING_KEY),
                        any());
        verify(channel).basicAck(1L, false);
    }

    @Test
    void consume_dlqPublishFailure_nacksOriginal() throws Exception {
        Message message =
                message(
                        new DomainEventEnvelope(
                                null,
                                MessageEventTypes.MESSAGE_SENT_V1,
                                OffsetDateTime.now(ZoneOffset.UTC),
                                SENDER_ID,
                                "message",
                                MESSAGE_ID,
                                Map.of(
                                        "conversationId",
                                        CONVERSATION_ID.toString(),
                                        "messageId",
                                        MESSAGE_ID.toString())));
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

    private DomainEventEnvelope envelope() {
        return new DomainEventEnvelope(
                EVENT_ID,
                MessageEventTypes.MESSAGE_SENT_V1,
                OffsetDateTime.now(ZoneOffset.UTC),
                SENDER_ID,
                "message",
                MESSAGE_ID,
                Map.of(
                        "conversationId",
                        CONVERSATION_ID.toString(),
                        "messageId",
                        MESSAGE_ID.toString()));
    }
}
