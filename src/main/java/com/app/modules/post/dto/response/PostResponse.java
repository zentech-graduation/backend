package com.app.modules.post.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.app.common.response.UserSummaryResponse;
import com.app.modules.hashtag.dto.response.HashtagSummaryResponse;
import com.app.modules.post.enums.PostStatus;
import com.app.modules.post.enums.PostType;

import io.swagger.v3.oas.annotations.media.Schema;

/** API response for a post, including ordered media and trigger-maintained counters. */
@Schema(description = "Post with ordered media and engagement counters")
public record PostResponse(
        @Schema(description = "Post identifier.") UUID id,
        @Schema(description = "Post author.") UserSummaryResponse author,
        @Schema(description = "Post caption.", nullable = true) String caption,
        @Schema(description = "Post type.") PostType postType,
        @Schema(description = "Lifecycle status.") PostStatus status,
        @Schema(description = "Number of likes; trigger-maintained.") int likeCount,
        @Schema(description = "Number of comments; trigger-maintained.") int commentCount,
        @Schema(description = "Number of saves; trigger-maintained.") int saveCount,
        @Schema(description = "Whether the viewer has liked this post.") boolean isLiked,
        @Schema(description = "Whether the viewer has saved this post.") boolean isSaved,
        @Schema(
                        description =
                                "Whether the viewer has already reported this post. True exactly"
                                        + " when a new report from this viewer against this post"
                                        + " would be rejected as a duplicate, so a client can"
                                        + " disable the report control instead of submitting and"
                                        + " handling the rejection. Resolved and dismissed"
                                        + " reports no longer make this true. Always false for an"
                                        + " anonymous viewer.",
                        example = "false")
                boolean hasReported,
        @Schema(description = "Number of views; updated by a background job and may lag.")
                int viewCount,
        @Schema(description = "Free-form location label.", nullable = true) String locationName,
        @Schema(description = "Latitude in decimal degrees.", nullable = true) BigDecimal latitude,
        @Schema(description = "Longitude in decimal degrees.", nullable = true)
                BigDecimal longitude,
        @Schema(description = "Ordered media items.") List<PostMediaResponse> media,
        @Schema(
                        description =
                                "Hashtags this post is associated with, ordered by name. A hashtag"
                                        + " an administrator has deleted is omitted, while the"
                                        + " caption keeps its literal #tag text, so a client"
                                        + " renders that text without linking it. A banned hashtag"
                                        + " is still listed: banning hides the tag's own surfaces,"
                                        + " not the posts that used it. Empty for a post whose"
                                        + " caption carries no tag, and for any post that is not"
                                        + " published, because associations exist only while a post"
                                        + " is published.")
                List<HashtagSummaryResponse> hashtags,
        @Schema(description = "Creation timestamp.") OffsetDateTime createdAt,
        @Schema(description = "Last update timestamp.") OffsetDateTime updatedAt) {}
