package com.app.modules.comment.api;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.app.common.ApiConstants;
import com.app.common.response.ApiResponse;
import com.app.common.response.CursorPageResponse;
import com.app.modules.comment.dto.request.CreateCommentRequest;
import com.app.modules.comment.dto.request.EditCommentRequest;
import com.app.modules.comment.dto.response.CommentResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** OpenAPI contract for comment creation, nested replies, likes, edit, and soft delete. */
@Tag(
        name = "Comments",
        description = "Comment creation, nested replies, likes, edit and soft delete")
public interface CommentApi {

    @Operation(
            summary = "Create a comment or reply",
            description =
                    "Creates a top-level comment or a reply (when parentId is set) on the path post."
                            + " The body post id must match the path post id. Content is normalized and"
                            + " moderated synchronously. An optional Idempotency-Key header replays the"
                            + " original response on retry.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "Comment created",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CommentResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Validation failure, path/body mismatch, or depth exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Commenting not permitted on this post",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Post or parent comment not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Idempotency key reused with a different payload",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "422",
                description = "Content rejected by moderation",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Slow mode active or rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping(ApiConstants.Posts.ROOT + ApiConstants.Posts.COMMENTS)
    ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @PathVariable("postId") UUID postId,
            @Valid @RequestBody CreateCommentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey);

    @Operation(
            summary = "List top-level comments for a post",
            description =
                    "Cursor-paginated approved top-level comments, newest first. The first page"
                            + " only is preceded by up to three top comments ranked by like count,"
                            + " each flagged with `pinned: true` and additional to the requested"
                            + " page size; a pinned comment is not repeated in the same page's"
                            + " newest-first body. Requires authentication; private posts are"
                            + " visible only to the owner and accepted followers.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Cursor page of comments",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping(ApiConstants.Posts.ROOT + ApiConstants.Posts.COMMENTS)
    ResponseEntity<ApiResponse<CursorPageResponse<CommentResponse>>> listTopLevelComments(
            @PathVariable("postId") UUID postId,
            @Parameter(description = "Opaque cursor from the previous page")
                    @RequestParam(value = "cursor", required = false)
                    String cursor,
            @Parameter(description = "Page size (1–100, default 20)")
                    @RequestParam(value = "limit", defaultValue = "20")
                    int limit);

    @Operation(
            summary = "List replies to a comment",
            description =
                    "Cursor-paginated approved direct replies to a comment, newest first. Requires"
                            + " authentication; private posts are visible only to the owner and accepted"
                            + " followers.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Cursor page of replies",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Parent comment not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping(ApiConstants.Comments.ROOT + ApiConstants.Comments.REPLIES)
    ResponseEntity<ApiResponse<CursorPageResponse<CommentResponse>>> listReplies(
            @PathVariable("commentId") UUID commentId,
            @Parameter(description = "Opaque cursor from the previous page")
                    @RequestParam(value = "cursor", required = false)
                    String cursor,
            @Parameter(description = "Page size (1–100, default 20)")
                    @RequestParam(value = "limit", defaultValue = "20")
                    int limit);

    @Operation(
            summary = "Edit a comment",
            description = "Replaces the body of an owned comment; content is re-moderated.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Updated comment",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CommentResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Requester is not the comment owner",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Comment not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "422",
                description = "Content rejected by moderation",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping(ApiConstants.Comments.ROOT + ApiConstants.Comments.BY_ID)
    ResponseEntity<ApiResponse<CommentResponse>> editComment(
            @PathVariable("commentId") UUID commentId,
            @Valid @RequestBody EditCommentRequest request);

    @Operation(
            summary = "Delete a comment",
            description =
                    "Soft-deletes a comment and its entire subtree. Owner or admin only; returns"
                            + " 200 with an empty body.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Comment soft-deleted",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Requester is neither the owner nor an admin",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Comment not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @DeleteMapping(ApiConstants.Comments.ROOT + ApiConstants.Comments.BY_ID)
    ResponseEntity<ApiResponse<Void>> deleteComment(@PathVariable("commentId") UUID commentId);

    @Operation(summary = "Like a comment", description = "Adds the caller's like to a comment.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Comment liked",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Cannot like your own comment",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Comment not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Comment already liked",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping(ApiConstants.Comments.ROOT + ApiConstants.Comments.LIKE)
    ResponseEntity<ApiResponse<Void>> likeComment(@PathVariable("commentId") UUID commentId);

    @Operation(
            summary = "Unlike a comment",
            description = "Removes the caller's like from a comment.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Comment unliked",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Comment not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Comment has not been liked",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @DeleteMapping(ApiConstants.Comments.ROOT + ApiConstants.Comments.LIKE)
    ResponseEntity<ApiResponse<Void>> unlikeComment(@PathVariable("commentId") UUID commentId);
}
