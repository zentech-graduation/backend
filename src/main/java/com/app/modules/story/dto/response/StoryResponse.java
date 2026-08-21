package com.app.modules.story.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.app.modules.story.enums.StoryType;

import io.swagger.v3.oas.annotations.media.Schema;

/** API response for a single story with its media and author display fields. */
@Schema(description = "Story with media, author, and viewer-context fields")
public record StoryResponse(
        @Schema(description = "Story identifier.") UUID id,
        @Schema(description = "Owner user identifier.") UUID userId,
        @Schema(description = "Owner username.", nullable = true) String username,
        @Schema(description = "Owner display name.", nullable = true) String userDisplayName,
        @Schema(description = "Owner avatar CDN URL.", nullable = true) String userAvatarUrl,
        @Schema(description = "Story media kind.") StoryType storyType,
        @Schema(description = "Caption overlaid on the story.", nullable = true) String caption,
        @Schema(description = "Backing media asset.") StoryMediaResponse media,
        @Schema(
                        description = "Number of unique viewers; only populated for the owner.",
                        nullable = true)
                Integer viewCount,
        @Schema(
                        description =
                                "Whether the requesting viewer has seen this story; null for owner.",
                        nullable = true)
                Boolean seen,
        @Schema(description = "Number of likes; only populated for the owner.", nullable = true)
                Integer likeCount,
        @Schema(description = "Whether the requesting viewer has liked this story.") boolean liked,
        @Schema(description = "Creation timestamp.") OffsetDateTime createdAt,
        @Schema(description = "Expiry timestamp; the story is gone from reads after this.")
                OffsetDateTime expiresAt) {}
