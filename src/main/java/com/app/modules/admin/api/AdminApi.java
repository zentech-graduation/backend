package com.app.modules.admin.api;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.app.common.ApiConstants;
import com.app.common.response.ApiResponse;
import com.app.common.response.CursorPageResponse;
import com.app.modules.admin.dto.request.CommentModerationActionRequest;
import com.app.modules.admin.dto.request.PostModerationActionRequest;
import com.app.modules.admin.dto.request.ReportResolutionActionRequest;
import com.app.modules.admin.dto.request.UserStatusActionRequest;
import com.app.modules.admin.dto.response.AdminActionResponse;
import com.app.modules.admin.enums.AdminActionType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** OpenAPI contract for role-restricted moderation actions and immutable audit history. */
@Tag(name = "Administration", description = "Moderation actions and immutable audit history")
@RequestMapping(ApiConstants.Admin.ROOT)
public interface AdminApi {

    /** Changes a user status and returns the immutable audit event. */
    @Operation(
            summary = "Moderate a user account",
            description =
                    "Bans, unbans, suspends, or unsuspends a user atomically with an audit event.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "User status updated",
                content = @Content(schema = @Schema(implementation = AdminActionResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Action does not apply to users",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Moderator or administrator role required",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "User not found",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "User is already in the requested state",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping(ApiConstants.Admin.USER_STATUS)
    ResponseEntity<ApiResponse<AdminActionResponse>> updateUserStatus(
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody UserStatusActionRequest request);

    /** Removes or restores a post and returns the immutable audit event. */
    @Operation(
            summary = "Moderate a post",
            description =
                    "Removes or restores a post atomically with an optional report link and audit event.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Post moderation applied",
                content = @Content(schema = @Schema(implementation = AdminActionResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Action does not apply to posts",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Moderator or administrator role required",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Post or linked report not found",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Post is already in the requested state",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping(ApiConstants.Admin.POST_STATUS)
    ResponseEntity<ApiResponse<AdminActionResponse>> moderatePost(
            @PathVariable("postId") UUID postId,
            @Valid @RequestBody PostModerationActionRequest request);

    /** Removes or restores a comment and returns the immutable audit event. */
    @Operation(
            summary = "Moderate a comment",
            description =
                    "Removes or restores a comment atomically with an optional report link and audit event.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Comment moderation applied",
                content = @Content(schema = @Schema(implementation = AdminActionResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Action does not apply to comments",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Moderator or administrator role required",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Comment or linked report not found",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Comment is already in the requested state",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping(ApiConstants.Admin.COMMENT_STATUS)
    ResponseEntity<ApiResponse<AdminActionResponse>> moderateComment(
            @PathVariable("commentId") UUID commentId,
            @Valid @RequestBody CommentModerationActionRequest request);

    /** Resolves or dismisses a report and returns the immutable audit event. */
    @Operation(
            summary = "Close a report",
            description =
                    "Resolves or dismisses a report while recording reviewer metadata and an audit event.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Report closed",
                content = @Content(schema = @Schema(implementation = AdminActionResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Action does not apply to reports",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Moderator or administrator role required",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Report not found",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Report is already terminal",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping(ApiConstants.Admin.REPORT_STATUS)
    ResponseEntity<ApiResponse<AdminActionResponse>> resolveReport(
            @PathVariable("reportId") UUID reportId,
            @Valid @RequestBody ReportResolutionActionRequest request);

    /** Lists audit events with optional filters and cursor pagination. */
    @Operation(
            summary = "List moderation audit events",
            description =
                    "Returns immutable audit events ordered newest first with optional filters.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Audit event page returned",
                content = @Content(schema = @Schema(implementation = CursorPageResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Moderator or administrator role required",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping(ApiConstants.Admin.ACTIONS)
    ResponseEntity<ApiResponse<CursorPageResponse<AdminActionResponse>>> listActions(
            @RequestParam(required = false) UUID adminId,
            @RequestParam(required = false) UUID targetUserId,
            @RequestParam(required = false) AdminActionType actionType,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size);

    /** Returns one audit event by identifier. */
    @Operation(summary = "Get a moderation audit event")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Audit event returned",
                content = @Content(schema = @Schema(implementation = AdminActionResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Moderator or administrator role required",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Audit event not found",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping(ApiConstants.Admin.ACTION_BY_ID)
    ResponseEntity<ApiResponse<AdminActionResponse>> getAction(
            @PathVariable("actionId") UUID actionId);
}
