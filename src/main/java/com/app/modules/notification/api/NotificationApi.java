package com.app.modules.notification.api;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.app.common.ApiConstants;
import com.app.common.response.ApiResponse;
import com.app.common.response.CursorPageResponse;
import com.app.common.security.user.UserPrincipal;
import com.app.modules.notification.dto.response.NotificationResponse;
import com.app.modules.notification.dto.response.UnreadCountResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** OpenAPI contract for the notification module. */
@Tag(name = "Notifications", description = "In-app notification listing and read-state management")
@RequestMapping(ApiConstants.Notifications.ROOT)
public interface NotificationApi {

    /** Returns a cursor-paginated page of the caller's notifications; requires a bearer JWT. */
    @Operation(summary = "List notifications")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Notification page returned",
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
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping
    ResponseEntity<ApiResponse<CursorPageResponse<NotificationResponse>>> listNotifications(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit);

    /** Marks one notification as read; 403 if not owned by the caller; requires a bearer JWT. */
    @Operation(summary = "Mark a notification as read")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Notification marked as read",
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
                description = "Notification not found or belongs to a different user",
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
    @PatchMapping(ApiConstants.Notifications.MARK_READ)
    ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable UUID notificationId, @AuthenticationPrincipal UserPrincipal principal);

    /** Marks all of the caller's unread notifications as read; requires a bearer JWT. */
    @Operation(summary = "Mark all notifications as read")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "All notifications marked as read",
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
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping(ApiConstants.Notifications.MARK_ALL_READ)
    ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @AuthenticationPrincipal UserPrincipal principal);

    /** Returns the count of the caller's unread notifications; requires a bearer JWT. */
    @Operation(summary = "Get unread notification count")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Unread count returned",
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
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping(ApiConstants.Notifications.UNREAD_COUNT)
    ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
            @AuthenticationPrincipal UserPrincipal principal);
}
