package com.app.modules.comment.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.app.common.response.UserSummaryResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/** API response representing a single comment. */
@Schema(description = "A comment on a post")
public record CommentResponse(
        @Schema(description = "Comment identifier.") UUID id,
        @Schema(description = "Post the comment belongs to.") UUID postId,
        @Schema(description = "Author of the comment.") UserSummaryResponse author,
        @Schema(description = "Parent comment identifier; null for a top-level comment.")
                UUID parentId,
        @Schema(description = "Top-level ancestor identifier; null for a top-level comment.")
                UUID rootId,
        @Schema(description = "Nesting depth, 0 for top-level.") short depth,
        @Schema(description = "Comment body.") String content,
        @Schema(description = "Trigger-maintained like count.") int likeCount,
        @Schema(description = "Whether the viewer has liked this comment.") boolean isLiked,
        @Schema(description = "Trigger-maintained direct reply count.") int replyCount,
        @Schema(description = "Creation timestamp.") OffsetDateTime createdAt,
        @Schema(description = "Last update timestamp.") OffsetDateTime updatedAt,
        @Schema(
                        description =
                                "Whether this comment was returned as part of the pinned"
                                        + " top-comments block. Only ever true on the first page of a"
                                        + " post's comment list; always false elsewhere, including on"
                                        + " single-comment responses.")
                boolean pinned) {

    /**
     * Returns a copy marked as part of the pinned top-comments block.
     *
     * @return the same comment with {@code pinned} set to true
     */
    public CommentResponse asPinned() {
        return new CommentResponse(
                id,
                postId,
                author,
                parentId,
                rootId,
                depth,
                content,
                likeCount,
                isLiked,
                replyCount,
                createdAt,
                updatedAt,
                true);
    }
}
