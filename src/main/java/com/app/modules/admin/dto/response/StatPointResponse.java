package com.app.modules.admin.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** One stored value in a metric series. */
@Schema(description = "A single point in a metric series")
public record StatPointResponse(
        @Schema(description = "Inclusive start of the bucket this value covers")
                OffsetDateTime bucketStart,
        @Schema(
                        description =
                                "Breakdown key, empty for a metric with no breakdown. A key absent"
                                        + " from a bucket means zero.",
                        example = "active")
                String dimension,
        @Schema(description = "The recorded number", example = "37") long value) {}
