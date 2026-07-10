package com.app.modules.recommendation.service;

import java.math.BigDecimal;
import java.util.Set;

import com.app.modules.recommendation.enums.RecommendationEventType;

/**
 * In-memory cached read of the {@code recommendation_event_weights} config table.
 *
 * <p>The Java trending updater, the blend scorer, and the serving path all read weights through
 * this service so tuning is metadata-driven. A {@code null} return means the event type has no
 * configured weight (treated as zero contribution by callers).
 */
public interface RecommendationEventWeightService {

    /** Returns the configured weight for the event type, or {@code null} if no row exists. */
    BigDecimal weight(RecommendationEventType eventType);

    /** Event types enabled for the collaborative-filter subsystem. */
    Set<RecommendationEventType> cfEnabled();

    /** Event types enabled for the trending subsystem. */
    Set<RecommendationEventType> trendingEnabled();

    /** Event types enabled for the author-affinity subsystem. */
    Set<RecommendationEventType> affinityEnabled();

    /** Forces a reload from the database; used by tests and by manual cache-bust endpoints. */
    void refresh();
}
