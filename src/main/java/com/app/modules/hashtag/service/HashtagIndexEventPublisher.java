package com.app.modules.hashtag.service;

import static com.app.modules.hashtag.messaging.HashtagEventTypes.HASHTAG_INDEX_DELETE_V1;
import static com.app.modules.hashtag.messaging.HashtagEventTypes.HASHTAG_INDEX_UPSERT_V1;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.app.common.outbox.service.OutboxService;

/**
 * Single point that enqueues hashtag search-index events.
 *
 * <p>Carries no transaction annotation so every call joins the caller's transaction, which is what
 * {@code OutboxService.enqueue} requires and what makes the event and the row change commit or roll
 * back together.
 *
 * <p>Every event carries the hashtag id and nothing else. The consumer reads name, {@code
 * post_count} and {@code status} from the source-of-truth database, so it decides between indexing
 * and dropping the document against current state rather than against whatever was true when the
 * event was written. That is what lets a status change reuse the upsert event instead of needing an
 * event type of its own, and it keeps user free-text out of the payload, which the outbox rejects.
 */
@Component
public class HashtagIndexEventPublisher {

    private static final String AGGREGATE_TYPE_HASHTAG = "hashtag";

    private final OutboxService outboxService;

    public HashtagIndexEventPublisher(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    /**
     * Enqueues a sync event that leaves the index decision to the consumer.
     *
     * @param hashtagId the hashtag whose index document should be reconciled
     */
    public void enqueueSync(UUID hashtagId) {
        enqueue(HASHTAG_INDEX_UPSERT_V1, hashtagId);
    }

    /**
     * Enqueues an unconditional index delete.
     *
     * <p>Used when the caller already knows the hashtag has no live posts, so the consumer need not
     * re-derive that.
     *
     * @param hashtagId the hashtag whose index document should be removed
     */
    public void enqueueDelete(UUID hashtagId) {
        enqueue(HASHTAG_INDEX_DELETE_V1, hashtagId);
    }

    private void enqueue(String eventType, UUID hashtagId) {
        outboxService.enqueue(
                eventType,
                eventType,
                AGGREGATE_TYPE_HASHTAG,
                hashtagId,
                null,
                Map.of("hashtagId", hashtagId.toString()));
    }
}
