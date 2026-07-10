package com.app.modules.recommendation.service;

import java.util.UUID;

import com.app.modules.recommendation.dto.request.ClientEventBatchRequest;

/**
 * Accepts client-side event batches and enqueues them through the transactional outbox.
 *
 * <p>The implementation derives {@code userId} from the authenticated principal and publishes one
 * {@code rec.impression.batch.v1} envelope per batch (not per item), so Postgres writes stay
 * consumer-side.
 */
public interface RecommendationEventIngestionService {

    /**
     * Validates and enqueues one client event batch.
     *
     * @param userId authenticated user id; the client cannot supply this
     * @param request client batch payload
     */
    void ingestBatch(UUID userId, ClientEventBatchRequest request);
}
