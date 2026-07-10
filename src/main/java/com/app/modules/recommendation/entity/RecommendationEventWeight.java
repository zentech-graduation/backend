package com.app.modules.recommendation.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.app.modules.recommendation.enums.RecommendationEventType;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Metadata row for one {@code event_type} value, following the GLOBAL_RULES enum-vs-config-table
 * contract: the enum constrains which values can be written, this table controls how each value is
 * weighted across the collaborative-filter, trending, and affinity subsystems.
 */
@Entity
@Table(name = "recommendation_event_weights")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationEventWeight {

    // STRING enum mapping is used instead of an AttributeConverter because Hibernate forbids
    // converters on @Id attributes; the enum constant names are lowercase to match the PG enum.
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false)
    private RecommendationEventType eventType;

    @Setter
    @Column(name = "weight", nullable = false, precision = 6, scale = 3)
    private BigDecimal weight;

    @Setter
    @Column(name = "cf_enabled", nullable = false)
    private boolean cfEnabled;

    @Setter
    @Column(name = "trending_enabled", nullable = false)
    private boolean trendingEnabled;

    @Setter
    @Column(name = "affinity_enabled", nullable = false)
    private boolean affinityEnabled;

    @Setter
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
