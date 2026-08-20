package com.app.modules.admin.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The most recent statistics snapshot, read from storage rather than computed.
 *
 * <p>Everything except {@code topHashtags} comes from the newest collected bucket. None of it is
 * computed at request time: the counts behind these numbers are whole-table aggregates, which is
 * seconds of work at production size and not something a request path can absorb. {@code
 * computedAt} is carried so a client can show the staleness honestly rather than implying the
 * numbers are live.
 *
 * <p>A dimension absent from a breakdown means zero. The collection job produces no row for an
 * empty group.
 */
@Schema(description = "Newest stored statistics snapshot")
public record AdminStatsCurrentResponse(
        @Schema(
                        description =
                                "Bucket the snapshot belongs to; null when nothing has been"
                                        + " collected yet",
                        nullable = true)
                OffsetDateTime bucketStart,
        @Schema(description = "When the collection job computed this bucket", nullable = true)
                OffsetDateTime computedAt,
        @Schema(description = "Accounts that exist and are not deleted", example = "10432")
                long totalUsers,
        @Schema(description = "Account count by lifecycle status") Map<String, Long> usersByStatus,
        @Schema(description = "Account count by role") Map<String, Long> usersByRole,
        @Schema(description = "Published posts that are not deleted", example = "88120")
                long totalPosts,
        @Schema(description = "Comments that are not deleted", example = "402311")
                long totalComments,
        @Schema(description = "Stories that are neither deleted nor expired", example = "1204")
                long totalStories,
        @Schema(description = "Report count by status") Map<String, Long> reportsByStatus,
        @Schema(description = "Report count by reason") Map<String, Long> reportsByReason,
        @Schema(description = "Most used active hashtags") List<TopHashtagResponse> topHashtags,
        @Schema(
                        description =
                                "True because topHashtags alone is computed at request time rather"
                                        + " than read from the snapshot, so it never lags",
                        example = "true")
                boolean topHashtagsLive) {}
