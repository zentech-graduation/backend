package com.app.modules.post.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.app.common.ApiConstants;
import com.app.common.config.openapi.AuthenticationRequiredResponse;
import com.app.common.config.openapi.CursorErrorResponses;
import com.app.common.response.ApiResponse;
import com.app.common.response.CursorPageResponse;
import com.app.modules.post.dto.request.CreatePostRequest;
import com.app.modules.post.dto.request.PostStatusTransitionRequest;
import com.app.modules.post.dto.request.UpdatePostCaptionRequest;
import com.app.modules.post.dto.response.FeedPostResponse;
import com.app.modules.post.dto.response.PostEditHistoryResponse;
import com.app.modules.post.dto.response.PostResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** OpenAPI contract for post creation, retrieval, lifecycle, edit history, and search. */
@Tag(name = "Posts", description = "Post creation, retrieval, lifecycle, edit history, and search")
@RequestMapping(ApiConstants.Posts.ROOT)
public interface PostApi {

    @Operation(
            summary = "Create a post",
            description =
                    "Creates a post. Text posts require a non-blank caption and no media."
                            + " Image and video posts require exactly one media asset of the"
                            + " matching type. Carousel posts require 2–10 media items."
                            + " Publishing extracts caption hashtags synchronously.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "415",
                description = "Request body was sent with an unsupported Content-Type",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "Post created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid payload, media cardinality, or initial status",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Media asset not owned by the author",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Media asset not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @AuthenticationRequiredResponse
    @PostMapping
    ResponseEntity<ApiResponse<PostResponse>> createPost(
            @Valid @RequestBody CreatePostRequest request);

    @Operation(
            summary = "Get a post",
            description =
                    "Returns a single post. Non-owners can only retrieve published posts from"
                            + " accounts visible to them.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Path variable is not a valid UUID",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Post"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Post hidden by a block or a private account",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Post not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @AuthenticationRequiredResponse
    @GetMapping(ApiConstants.Posts.BY_ID)
    ResponseEntity<ApiResponse<PostResponse>> getPostById(@PathVariable("postId") UUID postId);

    @Operation(
            summary = "Update a post caption",
            description =
                    "Replaces the caption of an owned post and appends an edit-history entry"
                            + " recording the previous caption. Published posts get their hashtag"
                            + " associations refreshed.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "415",
                description = "Request body was sent with an unsupported Content-Type",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Updated post"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Validation failure",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Requester is not the post owner",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Post not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @AuthenticationRequiredResponse
    @PatchMapping(ApiConstants.Posts.BY_ID)
    ResponseEntity<ApiResponse<PostResponse>> updateCaption(
            @PathVariable("postId") UUID postId,
            @Valid @RequestBody UpdatePostCaptionRequest request);

    @Operation(
            summary = "Transition a post lifecycle status",
            description =
                    "Valid transitions: draft to published, published to archived, archived to"
                            + " published (owner only), and any status to removed (owner or admin"
                            + " — equivalent to soft delete).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "415",
                description = "Request body was sent with an unsupported Content-Type",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Post after the transition"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid status transition",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Requester is not the owner (or admin for removal)",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Post not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @AuthenticationRequiredResponse
    @PatchMapping(ApiConstants.Posts.STATUS)
    ResponseEntity<ApiResponse<PostResponse>> transitionStatus(
            @PathVariable("postId") UUID postId,
            @Valid @RequestBody PostStatusTransitionRequest request);

    @Operation(
            summary = "Delete a post",
            description =
                    "Soft-deletes a post: sets deleted_at, flips status to removed, and removes"
                            + " hashtag associations. Owner or admin only.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "204",
                description = "Post soft-deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Requester is neither the owner nor an admin",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Post not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @AuthenticationRequiredResponse
    @DeleteMapping(ApiConstants.Posts.BY_ID)
    ResponseEntity<Void> deletePost(@PathVariable("postId") UUID postId);

    @Operation(
            summary = "Get the following feed",
            description =
                    "Cursor-paginated chronological feed of published posts from accounts the"
                            + " authenticated viewer follows with accepted status, newest first."
                            + " Posts from accounts involved in a block relationship with the viewer"
                            + " in either direction are excluded. Returns an empty page when the"
                            + " viewer has no accepted follows or when all followed accounts are"
                            + " blocked.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Cursor page of feed posts"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @CursorErrorResponses
    @AuthenticationRequiredResponse
    @GetMapping(ApiConstants.Posts.FEED)
    ResponseEntity<ApiResponse<CursorPageResponse<FeedPostResponse>>> getFeed(
            @Parameter(description = "Opaque cursor from the previous page")
                    @RequestParam(value = "cursor", required = false)
                    String cursor,
            @Parameter(description = "Page size (1–100, default 20)")
                    @RequestParam(value = "limit", defaultValue = "20")
                    @Min(1)
                    @Max(100)
                    int limit);

    @Operation(
            summary = "List a user's published posts",
            description =
                    "Cursor-paginated published posts of a user, newest first. Blocked viewers and"
                            + " non-followers of private accounts are rejected.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Cursor page of posts"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Blocked or private account without an accepted follow",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "User not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @CursorErrorResponses
    @AuthenticationRequiredResponse
    @GetMapping(ApiConstants.Posts.USER_POSTS)
    ResponseEntity<ApiResponse<CursorPageResponse<PostResponse>>> listUserPosts(
            @PathVariable("userId") UUID userId,
            @Parameter(
                            description =
                                    "Post types to include. Repeat the parameter for several"
                                            + " values, for example type=image&type=video; a"
                                            + " single comma-delimited value is also accepted."
                                            + " Omit it for no filter. An unrecognised value is"
                                            + " rejected with 400 rather than ignored. The"
                                            + " normalised set is bound into the returned cursor,"
                                            + " so a cursor from one filter cannot be replayed"
                                            + " under another.")
                    @RequestParam(value = "type", required = false)
                    List<String> type,
            @Parameter(description = "Opaque cursor from the previous page")
                    @RequestParam(value = "cursor", required = false)
                    String cursor,
            @Parameter(description = "Page size (1–100, default 20)")
                    @RequestParam(value = "limit", defaultValue = "20")
                    @Min(1)
                    @Max(100)
                    int limit);

    @Operation(
            summary = "List a post's caption edit history",
            description = "Cursor-paginated edit history of an owned post, newest first.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Cursor page of edit history entries"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Requester is not the post owner",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Post not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @CursorErrorResponses
    @AuthenticationRequiredResponse
    @GetMapping(ApiConstants.Posts.HISTORY)
    ResponseEntity<ApiResponse<CursorPageResponse<PostEditHistoryResponse>>> listEditHistory(
            @PathVariable("postId") UUID postId,
            @Parameter(description = "Opaque cursor from the previous page")
                    @RequestParam(value = "cursor", required = false)
                    String cursor,
            @Parameter(description = "Page size (1–100, default 20)")
                    @RequestParam(value = "limit", defaultValue = "20")
                    @Min(1)
                    @Max(100)
                    int limit);

    @Operation(
            summary = "Search posts",
            description =
                    "Full-text caption search over published posts via Elasticsearch. Returns an"
                            + " empty page when the search tier is unavailable.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Cursor page of matching posts"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Missing query parameter",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @AuthenticationRequiredResponse
    @GetMapping(ApiConstants.Posts.SEARCH)
    ResponseEntity<ApiResponse<CursorPageResponse<PostResponse>>> searchPosts(
            @Parameter(description = "Search term matched against captions", required = true)
                    @RequestParam("q")
                    String q,
            @Parameter(description = "Opaque cursor from the previous page")
                    @RequestParam(value = "cursor", required = false)
                    String cursor,
            @Parameter(description = "Page size (1–100, default 20)")
                    @RequestParam(value = "limit", defaultValue = "20")
                    @Min(1)
                    @Max(100)
                    int limit);
}
