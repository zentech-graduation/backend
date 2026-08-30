package com.app.modules.recommendation.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * One client flush of reported impressions.
 *
 * <p>An oversized batch is rejected outright rather than truncated, so a client never believes
 * signals were accepted that were in fact discarded.
 *
 * @param impressions the impressions in this flush; at least one, at most {@value #MAX_BATCH_SIZE}
 */
public record ImpressionBatchRequest(
        @NotEmpty @Size(max = MAX_BATCH_SIZE) @Valid List<ImpressionRequest> impressions) {

    public static final int MAX_BATCH_SIZE = 100;
}
