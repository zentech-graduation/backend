package com.app.modules.recommendation.messaging;

import com.app.common.messaging.RecommendationInteractionContract;

/**
 * Versioned recommendation-domain event types published through the transactional outbox.
 *
 * <p>Interaction events carry a single engagement ({@link #REC_INTERACTION_RECORDED_V1}); client
 * impression batches carry up to {@code app.recommendation.events.max-batch-size} items ({@link
 * #REC_IMPRESSION_BATCH_V1}). Routing key equals the event type, matching every other module in the
 * codebase.
 */
public final class RecommendationEventTypes {

    public static final String REC_INTERACTION_RECORDED_V1 =
            RecommendationInteractionContract.REC_INTERACTION_RECORDED_V1;
    public static final String REC_IMPRESSION_BATCH_V1 = "rec.impression.batch.v1";

    private RecommendationEventTypes() {}
}
