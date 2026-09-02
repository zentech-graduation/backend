package com.app.common.inbox.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import com.app.common.inbox.entity.ProcessedMessage;

public interface ProcessedMessageRepositoryCustom {

    Optional<ProcessedMessage> insertIfAbsent(String consumerName, UUID eventId, String eventType);

    /**
     * Deletes at most {@code batchSize} idempotency records processed strictly before the cutoff.
     *
     * <p>The cutoff must sit beyond the longest window in which a message can still be redelivered.
     * A row removed early stops guarding its event, and the next delivery of that event re-runs the
     * consumer's side effects.
     *
     * @param cutoff rows processed strictly before this instant are eligible
     * @param batchSize maximum rows to delete in this statement
     * @return the number of rows actually deleted, which the caller uses to detect the last batch
     */
    int deleteProcessedBefore(OffsetDateTime cutoff, int batchSize);
}
