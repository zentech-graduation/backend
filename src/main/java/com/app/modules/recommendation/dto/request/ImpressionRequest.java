package com.app.modules.recommendation.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import com.app.modules.recommendation.enums.ImpressionSurface;

/**
 * One reported impression of a post.
 *
 * @param impressionId client-generated identifier, stable across that client's own retries; it
 *     becomes the event id the whole ingest chain deduplicates on
 * @param postId the post that was seen
 * @param dwellSeconds how long the post stayed visible, in seconds
 * @param surface the surface the impression was reported from
 */
public record ImpressionRequest(
        @NotNull UUID impressionId,
        @NotNull UUID postId,
        @PositiveOrZero @DecimalMax("3600.0") double dwellSeconds,
        @NotNull ImpressionSurface surface) {}
