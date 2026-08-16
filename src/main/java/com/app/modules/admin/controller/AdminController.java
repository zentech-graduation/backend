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
import com.app.modules.admin.dto.request.AdminActionRequest;
import com.app.modules.admin.dto.response.AdminActionResponse;
import com.app.modules.admin.dto.response.AdminActionSummaryResponse;
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

    /** Bans a user for the authenticated administrator. */
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping(ApiConstants.Admin.BAN_USER)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AdminActionResponse>> banUser(
            @PathVariable("userId") UUID userId, @Valid @RequestBody AdminActionRequest request) {
        return ok(adminService.banUser(SecurityUtils.getCurrentUserId(), userId, request));
    }

    /** Unbans a user for the authenticated administrator. */
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping(ApiConstants.Admin.UNBAN_USER)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AdminActionResponse>> unbanUser(
            @PathVariable("userId") UUID userId, @Valid @RequestBody AdminActionRequest request) {
        return ok(adminService.unbanUser(SecurityUtils.getCurrentUserId(), userId, request));
    }

    /** Suspends a user for the authenticated administrator. */
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping(ApiConstants.Admin.SUSPEND_USER)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AdminActionResponse>> suspendUser(
            @PathVariable("userId") UUID userId, @Valid @RequestBody AdminActionRequest request) {
        return ok(adminService.suspendUser(SecurityUtils.getCurrentUserId(), userId, request));
    }

    /** Unsuspends a user for the authenticated administrator. */
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping(ApiConstants.Admin.UNSUSPEND_USER)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AdminActionResponse>> unsuspendUser(
            @PathVariable("userId") UUID userId, @Valid @RequestBody AdminActionRequest request) {
        return ok(adminService.unsuspendUser(SecurityUtils.getCurrentUserId(), userId, request));
    }

    /** Removes a post for the authenticated moderator or administrator. */
    @Override
    @PatchMapping(ApiConstants.Admin.REMOVE_POST)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AdminActionResponse>> removePost(
            @PathVariable("postId") UUID postId, @Valid @RequestBody AdminActionRequest request) {
        return ok(adminService.removePost(SecurityUtils.getCurrentUserId(), postId, request));
    }

    /** Restores a post for the authenticated moderator or administrator. */
    @Override
    @PatchMapping(ApiConstants.Admin.RESTORE_POST)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AdminActionResponse>> restorePost(
            @PathVariable("postId") UUID postId, @Valid @RequestBody AdminActionRequest request) {
        return ok(adminService.restorePost(SecurityUtils.getCurrentUserId(), postId, request));
    }

    /** Removes a comment for the authenticated moderator or administrator. */
    @Override
    @PatchMapping(ApiConstants.Admin.REMOVE_COMMENT)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AdminActionResponse>> removeComment(
            @PathVariable("commentId") UUID commentId,
            @Valid @RequestBody AdminActionRequest request) {
        return ok(adminService.removeComment(SecurityUtils.getCurrentUserId(), commentId, request));
    }

    /** Restores a comment for the authenticated moderator or administrator. */
    @Override
    @PatchMapping(ApiConstants.Admin.RESTORE_COMMENT)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AdminActionResponse>> restoreComment(
            @PathVariable("commentId") UUID commentId,
            @Valid @RequestBody AdminActionRequest request) {
        return ok(
                adminService.restoreComment(SecurityUtils.getCurrentUserId(), commentId, request));
    }

    /** Resolves a report for the authenticated moderator or administrator. */
    @Override
    @PatchMapping(ApiConstants.Admin.RESOLVE_REPORT)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AdminActionResponse>> resolveReport(
            @PathVariable("reportId") UUID reportId,
            @Valid @RequestBody AdminActionRequest request) {
        return ok(adminService.resolveReport(SecurityUtils.getCurrentUserId(), reportId, request));
    }

    /** Dismisses a report for the authenticated moderator or administrator. */
    @Override
    @PatchMapping(ApiConstants.Admin.DISMISS_REPORT)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AdminActionResponse>> dismissReport(
            @PathVariable("reportId") UUID reportId,
            @Valid @RequestBody AdminActionRequest request) {
        return ok(adminService.dismissReport(SecurityUtils.getCurrentUserId(), reportId, request));
    }

    /** Returns a filtered cursor page of audit summaries to a moderation actor. */
    @Override
    @GetMapping(ApiConstants.Admin.ACTIONS)
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<CursorPageResponse<AdminActionSummaryResponse>>> getActions(
            @RequestParam(required = false) UUID adminId,
            @RequestParam(required = false) AdminActionType actionType,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return page(adminService.getActions(adminId, actionType, cursor, limit));
    }

    /** Returns one audit event to an authenticated moderator or administrator. */
    @Override
    @GetMapping(ApiConstants.Admin.ACTION_BY_ID)
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AdminActionResponse>> getActionById(
            @PathVariable("actionId") UUID actionId) {
        return ok(adminService.getActionById(actionId));
    }

    /** Returns a cursor page of audit summaries for one affected user. */
    @Override
    @GetMapping(ApiConstants.Admin.ACTIONS_FOR_USER)
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<CursorPageResponse<AdminActionSummaryResponse>>>
            getActionsForUser(
                    @PathVariable("userId") UUID userId,
                    @RequestParam(required = false) String cursor,
                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return page(adminService.getActionsForUser(userId, cursor, limit));
    }

    private ResponseEntity<ApiResponse<AdminActionResponse>> ok(AdminActionResponse response) {
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK, response));
    }

    private ResponseEntity<ApiResponse<CursorPageResponse<AdminActionSummaryResponse>>> page(
            CursorPageResponse<AdminActionSummaryResponse> response) {
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK, response));
    }
}
