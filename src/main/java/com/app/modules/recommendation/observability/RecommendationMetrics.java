package com.app.modules.recommendation.observability;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/** Micrometer instrumentation for the recommendation feed pipeline. */
@Component
public class RecommendationMetrics {

    private final MeterRegistry registry;

    public RecommendationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * A source round returned candidates but none of them survived hydration and filtering - the
     * candidate ids did not resolve to a live, visible post. Distinct from an empty source
     * response, which is ordinary (an exhausted or cold-start recommender), not a data-integrity
     * signal.
     */
    public void zeroAcceptRound() {
        counter("zero_accept_round").increment();
    }

    /**
     * The ranked pipeline (Gorse personalized, then popularity, then trending topup) produced no
     * page-one content at all, so the request fell back to the chronological following feed. A
     * sustained rise here with a populated catalogue means the recommender's candidates are not
     * resolving to real posts, exactly as {@link #zeroAcceptRound()} would also show.
     */
    public void chronologicalFallback() {
        counter("chronological_fallback").increment();
    }

    private Counter counter(String outcome) {
        return registry.counter("recommendation.feed.fallback", "outcome", outcome);
    }
}
