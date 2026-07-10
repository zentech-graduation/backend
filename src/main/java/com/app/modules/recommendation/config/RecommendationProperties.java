package com.app.modules.recommendation.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Recommendation module knobs, bound under {@code app.recommendation.*}.
 *
 * <p>Phase 1 wires only the consumer toggle, partition automation, and the event-ingestion rate
 * limit. Later phases add serving, trending, blend, and re-rank knobs behind the same namespace.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.recommendation")
public class RecommendationProperties {

    /** Master switch for the RabbitMQ consumers (persist + trending). Defaults to off. */
    private boolean consumerEnabled = false;

    /** Toggle for the explore serving endpoint. Defaults to off until Phase 2 lands. */
    private boolean exploreEnabled = false;

    private final Partition partition = new Partition();

    private final Events events = new Events();

    @Getter
    @Setter
    public static class Partition {

        /** How far ahead to pre-create monthly partitions, in months from now. */
        private int lookaheadMonths = 2;

        /** Impressions retention window. Whole partitions older than this are dropped daily. */
        private int impressionsRetentionMonths = 3;
    }

    @Getter
    @Setter
    public static class Events {

        /** Max client events accepted per batch on {@code POST /api/v1/events}. */
        private int maxBatchSize = 50;

        /**
         * Min interval between client flushes is client-side; this caps server-side per-batch size
         * only.
         */
        private Duration ingestTimeout = Duration.ofSeconds(10);
    }
}
