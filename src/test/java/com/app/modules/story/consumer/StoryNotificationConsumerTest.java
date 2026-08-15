package com.app.modules.story.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
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

import com.app.common.inbox.enums.ProcessedMessageResult;
import com.app.common.inbox.service.ProcessedMessageService;
import com.app.common.messaging.DomainEventMessageParser;
import com.app.common.messaging.config.ConsumerRetryProperties;
import com.app.common.outbox.model.DomainEventEnvelope;
import com.app.common.outbox.model.DomainEventEnvelopeJson;
import com.app.modules.notification.entity.enums.NotificationType;
import com.app.modules.notification.service.NotificationService;
import com.app.modules.story.messaging.StoryEventTypes;
import com.rabbitmq.client.Channel;

@ExtendWith(MockitoExtension.class)
class StoryNotificationConsumerTest {

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VIEWER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID STORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Mock private ProcessedMessageService processedMessageService;
    @Mock private NotificationService notificationService;
    @Mock private Channel channel;

    private StoryNotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        // maxAttempts=1 with a zero backoff means any transient failure "exhausts" retry on the
        // first attempt without a real Thread.sleep, keeping this test fast and deterministic.
        ConsumerRetryProperties retryProperties = new ConsumerRetryProperties();
        retryProperties.setMaxAttempts(1);
        retryProperties.setRetryBackoffs(List.of(Duration.ZERO));
        consumer =
                new StoryNotificationConsumer(
                        new DomainEventMessageParser(),
                        processedMessageService,
                        notificationService,
                        retryProperties);
    }

    @Test
    void consume_storyViewed_createsStoryViewNotification() throws Exception {
        Message message = message(envelope());
        when(processedMessageService.processOnce(any(), any(), any(), any()))
                .thenAnswer(
                        inv -> {
                            inv.getArgument(3, Runnable.class).run();
                            return ProcessedMessageResult.PROCESSED;
                        });

        consumer.consume(message, channel);

        verify(notificationService)
                .create(VIEWER_ID, OWNER_ID, NotificationType.STORY_VIEW, "story", STORY_ID);
        verify(channel).basicAck(1L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
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
    void consume_missingStoryId_nacksWithoutRequeue() throws Exception {
        Message message =
                message(
                        new DomainEventEnvelope(
                                EVENT_ID,
                                StoryEventTypes.STORY_VIEWED_V1,
                                OffsetDateTime.now(ZoneOffset.UTC),
                                VIEWER_ID,
                                "story",
                                STORY_ID,
                                Map.of("ownerId", OWNER_ID.toString())));

        consumer.consume(message, channel);

        // false requeue lets the broker route the message per the queue's declared
        // dead-letter-exchange/routing-key topology instead of redelivering it.
        verify(channel).basicNack(1L, false, false);
        verify(channel, never()).basicAck(1L, false);
        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    void consume_missingOwnerId_nacksWithoutRequeue() throws Exception {
        Message message =
                message(
                        new DomainEventEnvelope(
                                EVENT_ID,
                                StoryEventTypes.STORY_VIEWED_V1,
                                OffsetDateTime.now(ZoneOffset.UTC),
                                VIEWER_ID,
                                "story",
                                STORY_ID,
                                Map.of("storyId", STORY_ID.toString())));

        consumer.consume(message, channel);

        verify(channel).basicNack(1L, false, false);
        verify(channel, never()).basicAck(1L, false);
    }

    @Test
    void consume_transientFailureExhaustsRetry_nacksWithoutRequeue() throws Exception {
        Message message = message(envelope());
        when(processedMessageService.processOnce(any(), any(), any(), any()))
                .thenThrow(new QueryTimeoutException("db down"));

        consumer.consume(message, channel);

        verify(channel).basicNack(1L, false, false);
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
                StoryEventTypes.STORY_VIEWED_V1,
                OffsetDateTime.now(ZoneOffset.UTC),
                VIEWER_ID,
                "story",
                STORY_ID,
                Map.of("storyId", STORY_ID.toString(), "ownerId", OWNER_ID.toString()));
    }
}
