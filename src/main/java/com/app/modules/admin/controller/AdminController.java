package com.app.modules.admin.controller;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.app.common.ApiConstants;
import com.app.common.base.BaseController;
import com.app.common.enums.ApiSuccessCode;
import com.app.common.response.ApiResponse;
import com.app.common.response.CursorPageResponse;
import com.app.common.security.util.SecurityUtils;
import com.app.modules.admin.api.AdminApi;
import com.app.modules.admin.dto.request.CommentModerationActionRequest;
import com.app.modules.admin.dto.request.PostModerationActionRequest;
import com.app.modules.admin.dto.request.ReportResolutionActionRequest;
import com.app.modules.admin.dto.request.UserStatusActionRequest;
import com.app.modules.admin.dto.response.AdminActionResponse;
import com.app.modules.admin.enums.AdminActionType;
import com.app.modules.admin.service.AdminService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

/** REST endpoints for role-restricted moderation workflows and audit-history retrieval. */
@RestController
@PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
public class AdminController extends BaseController implements AdminApi {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /** Applies a validated user status action for the authenticated moderation actor. */
    @Override
    @PatchMapping(ApiConstants.Admin.USER_STATUS)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AdminActionResponse>> updateUserStatus(
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody UserStatusActionRequest request) {
        return ok(adminService.updateUserStatus(SecurityUtils.getCurrentUserId(), userId, request));
    }

    /** Applies a validated post moderation action for the authenticated moderation actor. */
    @Override
    @PatchMapping(ApiConstants.Admin.POST_STATUS)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AdminActionResponse>> moderatePost(
            @PathVariable("postId") UUID postId,
            @Valid @RequestBody PostModerationActionRequest request) {
        return ok(adminService.moderatePost(SecurityUtils.getCurrentUserId(), postId, request));
    }

    /** Applies a validated comment moderation action for the authenticated moderation actor. */
    @Override
    @PatchMapping(ApiConstants.Admin.COMMENT_STATUS)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AdminActionResponse>> moderateComment(
            @PathVariable("commentId") UUID commentId,
            @Valid @RequestBody CommentModerationActionRequest request) {
        return ok(
                adminService.moderateComment(SecurityUtils.getCurrentUserId(), commentId, request));
    }

    /** Closes a report and records the authenticated moderation actor as its reviewer. */
    @Override
    @PatchMapping(ApiConstants.Admin.REPORT_STATUS)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AdminActionResponse>> resolveReport(
            @PathVariable("reportId") UUID reportId,
            @Valid @RequestBody ReportResolutionActionRequest request) {
        return ok(adminService.resolveReport(SecurityUtils.getCurrentUserId(), reportId, request));
    }

    /** Returns a filtered cursor page of audit events to an authenticated moderation actor. */
    @Override
    @GetMapping(ApiConstants.Admin.ACTIONS)
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<CursorPageResponse<AdminActionResponse>>> listActions(
            @RequestParam(required = false) UUID adminId,
            @RequestParam(required = false) UUID targetUserId,
            @RequestParam(required = false) AdminActionType actionType,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.OK,
                        adminService.listActions(adminId, targetUserId, actionType, cursor, size)));
    }

    /** Returns one audit event to an authenticated moderation actor. */
    @Override
    @GetMapping(ApiConstants.Admin.ACTION_BY_ID)
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AdminActionResponse>> getAction(
            @PathVariable("actionId") UUID actionId) {
        return ok(adminService.getAction(actionId));
    }

    private ResponseEntity<ApiResponse<AdminActionResponse>> ok(AdminActionResponse response) {
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK, response));
    }
}
