package com.app.modules.social.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.app.common.ApiConstants;
import com.app.common.response.ApiResponse;
import com.app.common.response.CursorPageResponse;
import com.app.modules.social.dto.response.FollowRequestResponse;
import com.app.modules.social.dto.response.FollowResponse;
import com.app.modules.social.dto.response.SocialUserSummaryResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** OpenAPI contract for the social module. */
@Tag(
        name = "Social",
        description =
                "User relationships, follow requests, blocking, followers, and following flows")
@RequestMapping(ApiConstants.Social.ROOT)
public interface SocialApi {

    @Operation(
            summary = "Follow a user or send follow request if account is private",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "Followed user or follow request created",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = FollowResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid follow action",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Missing or invalid access token",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "A block relationship prevents following",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Target user not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Follow relationship already exists or user is blocked",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/follow/{targetUserId}")
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<ApiResponse<FollowResponse>> follow(@PathVariable UUID targetUserId);

    @Operation(summary = "Unfollow a user or cancel pending follow request")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "204",
                description = "Unfollowed user or cancelled pending follow request"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Missing or invalid access token",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Target user or follow relationship not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @DeleteMapping("/follow/{targetUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    ResponseEntity<Void> unfollow(@PathVariable UUID targetUserId);

    @Operation(summary = "Get pending follow requests for current user")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Pending follow requests returned",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Missing or invalid access token",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping("/follow-requests")
    ResponseEntity<ApiResponse<List<FollowRequestResponse>>> getPendingFollowRequests();

    @Operation(summary = "Approve a pending follow request")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "204",
                description = "Follow request approved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Missing or invalid access token",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Pending follow request not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping("/follow-requests/{requesterId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    ResponseEntity<Void> approveFollowRequest(@PathVariable UUID requesterId);

    @Operation(summary = "Reject a pending follow request")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "204",
                description = "Follow request rejected"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Missing or invalid access token",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Pending follow request not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping("/follow-requests/{requesterId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    ResponseEntity<Void> rejectFollowRequest(@PathVariable UUID requesterId);

    @Operation(summary = "Block user and clean up follow relationship both ways")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "User blocked"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid block action",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Missing or invalid access token",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Target user not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/block/{targetUserId}")
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<ApiResponse<Void>> block(@PathVariable UUID targetUserId);

    @Operation(summary = "Unblock user")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "204",
                description = "User unblocked"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Missing or invalid access token",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Block relationship not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @DeleteMapping("/block/{targetUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    ResponseEntity<Void> unblock(@PathVariable UUID targetUserId);

    @Operation(summary = "Get followers with cursor pagination")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Followers returned",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CursorPageResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Missing or invalid access token",
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
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping("/users/{userId}/followers")
    ResponseEntity<ApiResponse<CursorPageResponse<SocialUserSummaryResponse>>> getFollowers(
            @PathVariable UUID userId,
            @RequestParam(required = false) @Size(max = 512) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit);

    @Operation(summary = "Get following list with cursor pagination")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Following list returned",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CursorPageResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Missing or invalid access token",
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
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping("/users/{userId}/following")
    ResponseEntity<ApiResponse<CursorPageResponse<SocialUserSummaryResponse>>> getFollowing(
            @PathVariable UUID userId,
            @RequestParam(required = false) @Size(max = 512) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit);
}
