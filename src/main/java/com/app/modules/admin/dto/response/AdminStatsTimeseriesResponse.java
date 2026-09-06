package com.app.modules.admin.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

import com.app.modules.admin.enums.StatGranularity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One metric's series over a window.
 *
 * <p>{@code granularity} is chosen by the server, not requested by the client, and is stated back
 * so a chart labels its axis correctly instead of assuming a width. Fine buckets exist only inside
 * the fine retention window; beyond it only the rolled-up daily rows survive.
 */
@Schema(description = "A metric's stored values across a window")
public record AdminStatsTimeseriesResponse(
        @Schema(description = "Metric that was read", example = "registrations") String metric,
        @Schema(description = "Bucket width the series was served at") StatGranularity granularity,
        @Schema(description = "Inclusive lower bound actually used") OffsetDateTime from,
        @Schema(description = "Exclusive upper bound actually used") OffsetDateTime to,
        @Schema(description = "Points, oldest first, then by dimension")
                List<StatPointResponse> points) {}
