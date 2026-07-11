package com.app.modules.recommendation.observability;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;

/** Micrometer instrumentation for the recommendation event-ingestion subsystem. */
@Component
public class RecommendationMetrics {

    private final MeterRegistry registry;

    public RecommendationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Increments the consumed-events counter tagged by consumer and outcome. */
    public void consumed(String consumer, String result) {
        registry.counter("rec.events.consumed", "consumer", consumer, "result", result).increment();
    }
}
