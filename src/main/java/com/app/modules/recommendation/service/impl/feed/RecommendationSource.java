package com.app.modules.recommendation.service.impl.feed;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.app.modules.recommendation.client.GorseClient;
import com.app.modules.recommendation.client.dto.GorseScore;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

/**
 * Candidate source stage of the recommendation feed pipeline.
 *
 * <p>Serves personalized candidates from Gorse and degrades to the non-personalized popularity
 * ranking when Gorse recommend is unavailable or the circuit is open. A separate bean (rather than
 * a private method) so the resilience proxy applies.
 */
@Slf4j
@Component
public class RecommendationSource {

    public static final char SOURCE_GORSE = 'g';
    public static final char SOURCE_POPULAR = 'p';

    private final GorseClient gorseClient;

    public RecommendationSource(GorseClient gorseClient) {
        this.gorseClient = gorseClient;
    }

    /** A batch of scored candidates tagged with the source that actually produced them. */
    public record SourceBatch(char source, List<GorseScore> scores) {}

    /**
     * Fetches the next candidate batch from the requested source.
     *
     * @param viewerId user the feed is for
     * @param source {@link #SOURCE_GORSE} or {@link #SOURCE_POPULAR}
     * @param n maximum candidates to fetch
     * @param offset zero-based offset into the source's ranked list
     * @return candidates from the requested source, or from the popularity fallback when the
     *     personalized source is unavailable; empty batch when no source can serve
     */
    @CircuitBreaker(name = "gorse", fallbackMethod = "popularFallback")
    public SourceBatch fetch(UUID viewerId, char source, int n, int offset) {
        if (source == SOURCE_POPULAR) {
            return new SourceBatch(SOURCE_POPULAR, gorseClient.popular(n, offset));
        }
        return new SourceBatch(SOURCE_GORSE, gorseClient.recommend(viewerId, n, offset));
    }

    // Resilience4j fallback: invoked when the primary throws OR the circuit is open. Only
    // availability failures may degrade; programming errors must surface to the caller.
    SourceBatch popularFallback(UUID viewerId, char source, int n, int offset, Throwable t) {
        if (!isAvailabilityFailure(t)) {
            if (t instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Unexpected recommendation source failure", t);
        }
        log.warn(
                "Gorse source '{}' unavailable ({}: {}), degrading",
                source,
                t.getClass().getSimpleName(),
                t.getMessage());
        if (source == SOURCE_POPULAR) {
            // Popularity itself failed; an empty batch signals the caller to fall through to the
            // chronological feed.
            return new SourceBatch(SOURCE_POPULAR, List.of());
        }
        try {
            return new SourceBatch(SOURCE_POPULAR, gorseClient.popular(n, offset));
        } catch (RestClientException e) {
            log.warn("Gorse popularity fallback also unavailable: {}", e.getMessage());
            return new SourceBatch(SOURCE_POPULAR, List.of());
        }
    }

    // Connection/timeout failures, open circuit, and 5xx responses count as unavailability; 4xx
    // responses indicate a client-side bug and must not be masked as degradation.
    private static boolean isAvailabilityFailure(Throwable t) {
        if (t instanceof CallNotPermittedException) {
            return true;
        }
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause instanceof ResourceAccessException || cause instanceof IOException) {
                return true;
            }
            if (cause instanceof RestClientResponseException response) {
                return response.getStatusCode().is5xxServerError();
            }
        }
        return false;
    }
}
