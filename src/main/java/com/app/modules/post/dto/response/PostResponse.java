package com.app.modules.post.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.app.common.response.UserSummaryResponse;
import com.app.modules.post.enums.PostStatus;
import com.app.modules.post.enums.PostType;

import io.swagger.v3.oas.annotations.media.Schema;

/** API response for a post, including ordered media and trigger-maintained counters. */
@Schema(description = "Post with ordered media and engagement counters")
public record PostResponse(
        @Schema(description = "Post identifier.") UUID id,
        @Schema(description = "Post author.") UserSummaryResponse author,
        @Schema(description = "Post caption.") String caption,
        @Schema(description = "Post type.") PostType postType,
        @Schema(description = "Lifecycle status.") PostStatus status,
        @Schema(description = "Number of likes; trigger-maintained.") int likeCount,
        @Schema(description = "Number of comments; trigger-maintained.") int commentCount,
        @Schema(description = "Number of saves; trigger-maintained.") int saveCount,
        @Schema(description = "Number of views; updated by a background job and may lag.")
                int viewCount,
        @Schema(description = "Free-form location label.") String locationName,
        @Schema(description = "Latitude in decimal degrees.") BigDecimal latitude,
        @Schema(description = "Longitude in decimal degrees.") BigDecimal longitude,
        @Schema(description = "Ordered media items.") List<PostMediaResponse> media,
        @Schema(description = "Creation timestamp.") OffsetDateTime createdAt,
        @Schema(description = "Last update timestamp.") OffsetDateTime updatedAt) {}
