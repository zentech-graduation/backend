package com.app.modules.post.consumer;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.stereotype.Component;

import com.app.common.config.rabbit.RabbitMqTopologyConfig;
import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.inbox.service.ProcessedMessageService;
import com.app.common.messaging.DeadLetterPublisher;
import com.app.common.messaging.DomainEventMessageParser;
import com.app.common.messaging.config.ConsumerRetryProperties;
import com.app.common.messaging.exception.PermanentMessageException;
import com.app.common.outbox.model.DomainEventEnvelope;
import com.app.modules.post.entity.Post;
import com.app.modules.post.enums.PostStatus;
import com.app.modules.post.event.PostIndexDeleteEvent;
import com.app.modules.post.event.PostIndexUpsertEvent;
import com.app.modules.post.messaging.PostEventTypes;
import com.app.modules.post.repository.PostRepository;
import com.app.modules.post.search.PostDocument;
import com.app.modules.post.search.PostSearchRepository;
import com.rabbitmq.client.Channel;

import tools.jackson.databind.ObjectMapper;

/**
 * RabbitMQ consumer that applies post index events to Elasticsearch.
 *
 * <p>Uses manual acknowledgement: ack after idempotent duplicate detection or successful side
 * effect, route poison messages to DLQ, and nack with requeue if DLQ publishing itself fails.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.post.consumer",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class PostIndexSyncConsumer {

    static final String CONSUMER_NAME = "post-index-sync-consumer";

    private static final Logger log = LoggerFactory.getLogger(PostIndexSyncConsumer.class);

    private final DomainEventMessageParser parser;
    private final ProcessedMessageService processedMessageService;
    private final DeadLetterPublisher deadLetterPublisher;
    private final ConsumerRetryProperties retryProperties;
    private final PostSearchRepository postSearchRepository;
    private final PostRepository postRepository;
    private final ObjectMapper objectMapper;
    private final Sleeper sleeper;

    @Autowired
    public PostIndexSyncConsumer(
            DomainEventMessageParser parser,
            ProcessedMessageService processedMessageService,
            DeadLetterPublisher deadLetterPublisher,
            ConsumerRetryProperties retryProperties,
            PostSearchRepository postSearchRepository,
            PostRepository postRepository,
            ObjectMapper objectMapper) {
        this(
                parser,
                processedMessageService,
                deadLetterPublisher,
                retryProperties,
                postSearchRepository,
                postRepository,
                objectMapper,
                Thread::sleep);
    }

    PostIndexSyncConsumer(
            DomainEventMessageParser parser,
            ProcessedMessageService processedMessageService,
            DeadLetterPublisher deadLetterPublisher,
            ConsumerRetryProperties retryProperties,
            PostSearchRepository postSearchRepository,
            PostRepository postRepository,
            ObjectMapper objectMapper,
            Sleeper sleeper) {
        this.parser = parser;
        this.processedMessageService = processedMessageService;
        this.deadLetterPublisher = deadLetterPublisher;
        this.retryProperties = retryProperties;
        this.postSearchRepository = postSearchRepository;
        this.postRepository = postRepository;
        this.objectMapper = objectMapper;
        this.sleeper = sleeper;
    }

    /**
     * Consumes a post index-sync event and applies it idempotently to Elasticsearch.
     *
     * @param message delivered RabbitMQ message carrying the domain event envelope
     * @param channel channel used for manual acknowledgement and DLQ routing
     * @throws IOException if acknowledgement or negative acknowledgement fails
     */
    @RabbitListener(queues = RabbitMqTopologyConfig.POST_INDEX_SYNC_QUEUE)
    public void consume(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            DomainEventEnvelope event = parser.parse(message);
            validateEnvelope(event);
            processWithRetry(event);
            channel.basicAck(deliveryTag, false);
        } catch (PermanentMessageException ex) {
            routeToDlqOrRequeue(message, channel, deliveryTag, ex);
        } catch (RuntimeException ex) {
            routeToDlqOrRequeue(message, channel, deliveryTag, ex);
        }
    }

    private void processWithRetry(DomainEventEnvelope event) {
        int maxAttempts = retryProperties.resolvedMaxAttempts();
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                processedMessageService.processOnce(
                        CONSUMER_NAME, event.eventId(), event.eventType(), () -> apply(event));
                return;
            } catch (RuntimeException ex) {
                if (isPermanent(ex)) {
                    throw ex;
                }
                if (!isTransient(ex)) {
                    throw ex;
                }
                lastFailure = ex;
                if (attempt >= maxAttempts) {
                    break;
                }
                sleepBeforeRetry(attempt);
            }
        }
        throw lastFailure;
    }

    private void apply(DomainEventEnvelope event) {
        switch (event.eventType()) {
            case PostEventTypes.POST_INDEX_UPSERT_V1 -> {
                PostIndexUpsertEvent payload =
                        objectMapper.convertValue(event.data(), PostIndexUpsertEvent.class);
                // Q4 gate: PostgreSQL is source of truth. A stale or out-of-order upsert against a
                // missing, soft-deleted, or non-published row is permanently dropped, not indexed.
                Optional<Post> post = postRepository.findById(payload.postId());
                if (post.isEmpty() || post.get().getStatus() != PostStatus.PUBLISHED) {
                    return;
                }
                // Caption is read from the source-of-truth row, not the event payload, so user
                // free-text never travels through the outbox.
                PostDocument document =
                        PostDocument.builder()
                                .id(payload.postId().toString())
                                .userId(payload.userId().toString())
                                .caption(post.get().getCaption())
                                .status("published")
                                .hashtagIds(payload.hashtagIds())
                                .createdAt(payload.createdAt())
                                .build();
                postSearchRepository.save(document);
            }
            case PostEventTypes.POST_INDEX_DELETE_V1 -> {
                PostIndexDeleteEvent payload =
                        objectMapper.convertValue(event.data(), PostIndexDeleteEvent.class);
                postSearchRepository.deleteById(payload.postId().toString());
            }
            default ->
                    throw new PermanentMessageException("unknown event type: " + event.eventType());
        }
    }

    private void validateEnvelope(DomainEventEnvelope event) {
        if (event == null || event.eventId() == null) {
            throw new PermanentMessageException("Event envelope or id is null");
        }
        if (event.eventType() == null || event.eventType().isBlank()) {
            throw new PermanentMessageException("Event type is missing");
        }
    }

    private void sleepBeforeRetry(int attempt) {
        Duration backoff = retryProperties.retryBackoffForAttempt(attempt);
        if (backoff.isZero()) {
            return;
        }
        try {
            sleeper.sleep(backoff.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during post index sync retry backoff", ex);
        }
    }

    private void routeToDlqOrRequeue(
            Message message, Channel channel, long deliveryTag, RuntimeException failure)
            throws IOException {
        try {
            deadLetterPublisher.publish(
                    message,
                    RabbitMqTopologyConfig.POST_INDEX_DEAD_LETTER_ROUTING_KEY,
                    failure.getMessage());
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException dlqFailure) {
            log.warn(
                    "Failed to publish post index sync event to DLQ; requeueing: {}",
                    dlqFailure.getMessage());
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private static boolean isPermanent(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof PermanentMessageException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    boolean isTransient(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof DataAccessException
                    || current instanceof RedisSystemException
                    || current instanceof AmqpException) {
                return true;
            }
            if (current instanceof AppException appException) {
                return appException.getErrorCode() == ApiErrorCode.SERVICE_UNAVAILABLE;
            }
            current = current.getCause();
        }
        return true;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
