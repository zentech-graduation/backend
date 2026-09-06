package com.app.modules.hashtag.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** A hashtag's ranking within a single trending snapshot window. */
@Schema(description = "A hashtag's rank and post count within a trending window")
public record HashtagTrendingResponse(
        @Schema(
                        description = "Unique hashtag identifier",
                        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
                UUID hashtagId,
        @Schema(description = "Hashtag name without the # prefix", example = "spring") String name,
        @Schema(description = "Number of posts tagged during the trending window", example = "87")
                int postCount,
        @Schema(
                        description = "Rank within this trending snapshot; 1 is the most popular",
                        example = "1")
                int rank,
        @Schema(
                        description = "Start of the trending window (UTC)",
                        example = "2024-01-15T00:00:00Z")
                OffsetDateTime periodStart,
        @Schema(description = "End of the trending window (UTC)", example = "2024-01-16T00:00:00Z")
                OffsetDateTime periodEnd) {}
