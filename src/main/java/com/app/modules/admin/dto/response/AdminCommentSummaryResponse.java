package com.app.modules.admin.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One comment as an investigating moderator sees it.
 *
 * <p>Carries no viewer state, for the same reason {@link AdminPostSummaryResponse} does not.
 *
 * @param id comment identifier
 * @param userId the author
 * @param username the author's username, or null once that account is deleted
 * @param postId the post the comment is on
 * @param parentId the comment replied to, or null for a top-level comment
 * @param content the comment body
 * @param moderationStatus the comment's own moderation state
 * @param removed whether the comment is soft-deleted, so a moderator does not act twice
 * @param likeCount trigger-maintained like count
 * @param createdAt when the comment was created
 */
@Schema(description = "One comment rendered for an administrative content review")
public record AdminCommentSummaryResponse(
        @Schema(description = "Comment identifier") UUID id,
        @Schema(description = "Author identifier") UUID userId,
        @Schema(
                        description = "Author's username; null once that account is deleted",
                        nullable = true)
                String username,
        @Schema(description = "Post the comment is on") UUID postId,
        @Schema(description = "Comment replied to; null for a top-level comment", nullable = true)
                UUID parentId,
        @Schema(description = "The comment body") String content,
        @Schema(description = "The comment's own moderation state", example = "approved")
                String moderationStatus,
        @Schema(description = "Whether the comment is soft-deleted") boolean removed,
        @Schema(description = "Number of likes; trigger-maintained") int likeCount,
        @Schema(description = "Creation timestamp") OffsetDateTime createdAt) {}
