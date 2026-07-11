package com.app.modules.recommendation.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Recommendation module knobs, bound under {@code app.recommendation.*}.
 *
 * <p>Phase 1 wires only the consumer toggle, partition automation, and the event-ingestion batch
 * cap. Later phases add serving, trending, blend, and re-rank knobs behind the same namespace.
 * Nested classes mirror the YAML nesting exactly ({@code consumer.enabled}, {@code
 * explore.enabled}); flat fields would silently bind to different keys.
 */
@Getter
@ConfigurationProperties(prefix = "app.recommendation")
public class RecommendationProperties {

    private final Consumer consumer = new Consumer();

    private final Explore explore = new Explore();

    private final Weights weights = new Weights();

    private final Partition partition = new Partition();

    private final Events events = new Events();

    @Getter
    @Setter
    public static class Consumer {

        /** Master switch for the RabbitMQ consumers (persist + trending). Defaults to off. */
        private boolean enabled = false;
    }

    @Getter
    @Setter
    public static class Explore {

        /** Toggle for the explore serving endpoint. Defaults to off until Phase 2 lands. */
        private boolean enabled = false;
    }

    @Getter
    @Setter
    public static class Weights {

        /**
         * Staleness bound for the in-memory event-weight snapshot; also drives the scheduled
         * refresh cadence.
         */
        private Duration refreshInterval = Duration.ofMinutes(5);
    }

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
    }
}
