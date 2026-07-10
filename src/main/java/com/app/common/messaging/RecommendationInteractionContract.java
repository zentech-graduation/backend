package com.app.common.messaging;

/**
 * Shared contract for recommendation interaction events, kept in {@code common} so producer modules
 * (post, comment, social) and the recommendation consumer depend on this constant rather than on
 * each other.
 *
 * <p>Server-side engagement hooks enqueue {@link #REC_INTERACTION_RECORDED_V1} on the
 * {@code social.events} exchange with the recommendation interaction payload. The recommendation
 * module's {@code RecommendationEventTypes} mirrors these values for the consumer side.
 */
public final class RecommendationInteractionContract {

    /** Event type and routing key for one server-side engagement recorded for recommendation. */
    public static final String REC_INTERACTION_RECORDED_V1 = "rec.interaction.recorded.v1";

    private RecommendationInteractionContract() {}
}
