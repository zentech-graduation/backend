package com.app.modules.comment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * API response carrying how many comments a subtree deletion covers.
 *
 * <p>Returned both by the pre-delete scope endpoint, where the number is what a delete would
 * remove, and by the delete itself, where it is what the delete did remove. One record serves both
 * so the two surfaces cannot drift apart on the definition of the number they report.
 */
@Schema(description = "How many comments a subtree deletion covers")
public record CommentDeletionScopeResponse(
        @Schema(
                        description =
                                "Number of comment rows the deletion soft-deletes. Counts the"
                                        + " target comment itself plus every descendant at any"
                                        + " depth, and excludes descendants already soft-deleted."
                                        + " This is not replyCount, which counts direct replies"
                                        + " only.",
                        example = "11")
                int deletedCommentCount) {}
