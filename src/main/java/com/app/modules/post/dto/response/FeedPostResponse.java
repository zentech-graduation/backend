package com.app.modules.post.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.app.modules.post.enums.PostStatus;
import com.app.modules.post.enums.PostType;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * API response for a post in the following feed, including ordered media, trigger-maintained
 * counters, and a reserved ranking field for forward compatibility.
 */
@Schema(
        description =
                "Post entry in the following feed, with engagement counters and a reserved ranking field")
public record FeedPostResponse(
        @Schema(description = "Post identifier.") UUID id,
        @Schema(description = "Author user identifier.") UUID userId,
        @Schema(description = "Author username.") String username,
        @Schema(description = "Author display name.") String userDisplayName,
        @Schema(description = "Author avatar CDN URL.") String userAvatarUrl,
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
        @Schema(description = "Last update timestamp.") OffsetDateTime updatedAt,
        @Schema(
                        description =
                                "Reserved for a future ranking score; always null in the current"
                                        + " chronological implementation. Present to keep the"
                                        + " contract forward-compatible with ranked ordering.")
                Double rankingScore) {}
