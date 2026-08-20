package com.app.modules.admin.api;

import java.time.OffsetDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.app.common.ApiConstants;
import com.app.common.config.openapi.AuthenticationRequiredResponse;
import com.app.common.response.ApiResponse;
import com.app.modules.admin.dto.response.AdminStatsCurrentResponse;
import com.app.modules.admin.dto.response.AdminStatsTimeseriesResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * OpenAPI contract for the administrative statistics surface.
 *
 * <p>Both operations read stored aggregates. Nothing here counts a whole table at request time: at
 * production size a single {@code COUNT(*)} over accounts is seconds of work, and posts and
 * comments are larger again. A background job absorbs that cost every half hour, where seconds do
 * not matter, and the snapshot carries the time it was computed so a client can show staleness
 * rather than imply the numbers are live.
 *
 * <p>Two kinds of metric are stored and they aggregate differently. Gauges are absolute snapshots
 * bounded by the end of their bucket; flows are direct counts of what happened inside the bucket. A
 * flow is never derived by subtracting consecutive gauges, so a moderation sweep cannot make "posts
 * created" read as a negative number.
 *
 * <p>Collection starts at the first bucket the process observed from its beginning, and there is no
 * backfill. A window reaching back before the deployment returns nothing for that stretch rather
 * than zeros.
 */
@Tag(name = "Administration", description = "Moderation actions and immutable audit history")
@RequestMapping(ApiConstants.Admin.ROOT)
public interface AdminStatsApi {

    /** Returns the newest stored statistics snapshot. */
    @Operation(
            summary = "Get the current platform snapshot",
            description =
                    "Reads the most recently collected bucket and computes none of it. Carries the"
                            + " bucket it belongs to and the time the job computed it. The most"
                            + " used hashtag list is the one exception: it is computed at request"
                            + " time, served by an index as a scan with a limit, and flagged as"
                            + " live. A breakdown key absent from a response means zero.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Snapshot returned; timestamps are null before the first collection"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Administrator role required",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @AuthenticationRequiredResponse
    @GetMapping(ApiConstants.Admin.STATS_CURRENT)
    ResponseEntity<ApiResponse<AdminStatsCurrentResponse>> getCurrentStats();

    /** Returns one metric's stored series over a window. */
    @Operation(
            summary = "Get a metric time series",
            description =
                    "Returns one metric's stored values, oldest first. Omitting both bounds gives"
                            + " the last 24 hours; supplying exactly one is refused rather than"
                            + " silently defaulting the other. Bucket width is decided by the"
                            + " server and stated in the response: fine buckets inside the fine"
                            + " retention window, rolled-up daily rows beyond it. Metric keys are"
                            + " users_total, users_by_status, users_by_role, posts_total,"
                            + " comments_total, stories_total, reports_by_status,"
                            + " reports_by_reason, registrations, posts_created, comments_created,"
                            + " follows_created, likes_created and admin_actions_by_type.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Series returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description =
                        "Unknown metric, only one bound supplied, 'to' not after 'from', or a"
                                + " window longer than one year",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Administrator role required",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @AuthenticationRequiredResponse
    @GetMapping(ApiConstants.Admin.STATS_TIMESERIES)
    ResponseEntity<ApiResponse<AdminStatsTimeseriesResponse>> getStatsTimeseries(
            @Parameter(
                            description =
                                    "Metric key to read. Defaults to registrations when omitted, so"
                                            + " a bare call returns something rather than an error.",
                            example = "registrations")
                    @RequestParam(defaultValue = "registrations")
                    String metric,
            @Parameter(
                            description = "Inclusive lower bound, ISO-8601 with offset",
                            example = "2026-08-18T00:00:00Z")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime from,
            @Parameter(
                            description = "Exclusive upper bound, ISO-8601 with offset",
                            example = "2026-08-19T00:00:00Z")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime to);
}
