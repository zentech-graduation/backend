package com.app.modules.recommendation.service;

import java.util.UUID;

import com.app.modules.recommendation.dto.request.ImpressionBatchRequest;

/** Domain API for ingesting batched post impressions reported by a client. */
public interface ImpressionService {

    /**
     * Records a batch of impressions for the viewer.
     *
     * <p>Each impression is enqueued on the transactional outbox under its client-supplied
     * impression id, so resubmitting a batch after a network failure enqueues nothing new. The call
     * never contacts the recommender, so it does not block on it, and it never writes {@code
     * posts.view_count}.
     *
     * @param viewerId authenticated viewer the impressions belong to
     * @param request the reported batch
     * @return how many impressions in the batch were newly enqueued
     */
    int recordBatch(UUID viewerId, ImpressionBatchRequest request);
}
