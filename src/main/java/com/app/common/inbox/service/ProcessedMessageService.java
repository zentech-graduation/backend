package com.app.common.inbox.service;

import java.util.UUID;

import com.app.common.inbox.enums.ProcessedMessageResult;

/** Service API for consumer-side idempotent message processing. */
public interface ProcessedMessageService {

    /**
     * Executes a consumer side effect once for a consumer and event pair.
     *
     * @param consumerName stable consumer identifier
     * @param eventId event identifier from the delivered envelope
     * @param eventType versioned event type from the delivered envelope
     * @param handler side effect to execute when this consumer has not processed the event
     * @return processing outcome indicating whether the handler ran or the event was a duplicate
     */
    ProcessedMessageResult processOnce(
            String consumerName, UUID eventId, String eventType, Runnable handler);
}
