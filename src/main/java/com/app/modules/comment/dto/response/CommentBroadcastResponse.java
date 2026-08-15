package com.app.modules.comment.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.app.common.response.UserSummaryResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Broadcast projection of a comment: every {@link CommentResponse} field except {@code isLiked} and
 * {@code pinned}.
 *
 * <p>One serialised blob is shared by every subscriber of a post's live comment stream, so a
 * viewer-dependent field cannot be resolved for it - the field is omitted entirely rather than
 * carrying a constant placeholder value, so a client reading a socket frame sees the key absent
 * instead of a value that looks correct but is not. {@code isLiked} is only ever correct on a
 * {@link CommentResponse} returned over REST.
 *
 * <p>{@code pinned} is omitted for a different reason: it describes a comment's position on a
 * requested page, not the comment itself, and a broadcast frame belongs to no page.
 */
@Schema(description = "A comment as broadcast to every live subscriber of a post's comment stream")
public record CommentBroadcastResponse(
        @Schema(description = "Comment identifier.") UUID id,
        @Schema(description = "Post the comment belongs to.") UUID postId,
        @Schema(description = "Author of the comment.") UserSummaryResponse author,
        @Schema(
                        description = "Parent comment identifier; null for a top-level comment.",
                        nullable = true)
                UUID parentId,
        @Schema(
                        description =
                                "Top-level ancestor identifier; null for a top-level comment.",
                        nullable = true)
                UUID rootId,
        @Schema(description = "Nesting depth, 0 for top-level.") short depth,
        @Schema(description = "Comment body.") String content,
        @Schema(description = "Trigger-maintained like count.") int likeCount,
        @Schema(description = "Trigger-maintained direct reply count.") int replyCount,
        @Schema(description = "Creation timestamp.") OffsetDateTime createdAt,
        @Schema(description = "Last update timestamp.") OffsetDateTime updatedAt) {}
