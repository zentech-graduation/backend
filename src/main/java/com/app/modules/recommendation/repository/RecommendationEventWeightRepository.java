package com.app.modules.recommendation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.app.modules.recommendation.entity.RecommendationEventWeight;
import com.app.modules.recommendation.enums.RecommendationEventType;

/** Spring Data JPA access to the {@code recommendation_event_weights} config table. */
public interface RecommendationEventWeightRepository
        extends JpaRepository<RecommendationEventWeight, RecommendationEventType> {

    List<RecommendationEventWeight> findByCfEnabledTrue();

    List<RecommendationEventWeight> findByTrendingEnabledTrue();
}
