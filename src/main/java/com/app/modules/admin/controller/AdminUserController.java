package com.app.modules.admin.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.app.common.ApiConstants;
import com.app.common.base.BaseController;
import com.app.common.enums.ApiSuccessCode;
import com.app.common.response.ApiResponse;
import com.app.common.response.CursorPageResponse;
import com.app.common.security.util.SecurityUtils;
import com.app.common.web.StrictQueryParameters;
import com.app.modules.admin.api.AdminUserApi;
import com.app.modules.admin.dto.request.AdminActionRequest;
import com.app.modules.admin.dto.request.AdminRoleChangeRequest;
import com.app.modules.admin.dto.response.AdminActionResponse;
import com.app.modules.admin.dto.response.AdminUserDetailResponse;
import com.app.modules.admin.dto.response.AdminUserListItemResponse;
import com.app.modules.admin.dto.response.AdminUserLookupResponse;
import com.app.modules.admin.service.AdminUserService;
import com.app.modules.users.enums.UserRole;
import com.app.modules.users.enums.UserStatus;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

/**
 * REST endpoints for the administrative account surface.
 *
 * <p>The class-level {@code @PreAuthorize} is the second gate, not the first: the {@code
 * /api/v1/admin/users/**} matcher in {@code SecurityConfig} already restricts this whole sub-tree
 * to ADMIN. Both are declared because a service reachable only through a path matcher stops being
 * protected the moment someone adds a route that does not fit the pattern.
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController extends BaseController implements AdminUserApi {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /** Returns a cursor page of accounts to the authenticated administrator. */
    @Override
    @GetMapping(ApiConstants.Admin.USERS)
    @StrictQueryParameters
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<CursorPageResponse<AdminUserListItemResponse>>> listUsers(
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return page(adminUserService.listUsers(status, role, cursor, limit));
    }

    /** Returns a cursor page of matching accounts to the authenticated administrator. */
    @Override
    @GetMapping(ApiConstants.Admin.USER_SEARCH)
    @StrictQueryParameters
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<CursorPageResponse<AdminUserListItemResponse>>> searchUsers(
            @RequestParam("q") String query,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return page(adminUserService.searchUsers(query, cursor, limit));
    }

    /** Returns one account's full administrative detail. */
    @Override
    @GetMapping(ApiConstants.Admin.USER_BY_ID)
    @StrictQueryParameters
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> getUserDetail(
            @PathVariable("userId") UUID userId) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.OK,
                        adminUserService.getUserDetail(SecurityUtils.getCurrentUserId(), userId)));
    }

    /** Revokes every live session of one account for the authenticated administrator. */
    @Override
    @PostMapping(ApiConstants.Admin.USER_FORCE_LOGOUT)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AdminActionResponse>> forceLogout(
            @PathVariable("userId") UUID userId, @Valid @RequestBody AdminActionRequest request) {
        return action(
                adminUserService.forceLogout(SecurityUtils.getCurrentUserId(), userId, request));
    }

    /** Resolves several account identifiers to display information in one call. */
    @Override
    @GetMapping(ApiConstants.Admin.USER_SUMMARIES)
    @StrictQueryParameters
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<List<AdminUserLookupResponse>>> resolveUserSummaries(
            @RequestParam("ids") List<UUID> ids) {
        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.OK, adminUserService.resolveUserSummaries(ids)));
    }

    /** Revokes one named session of one account for the authenticated administrator. */
    @Override
    @DeleteMapping(ApiConstants.Admin.USER_SESSION_BY_ID)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AdminActionResponse>> revokeSession(
            @PathVariable("userId") UUID userId,
            @PathVariable("sessionId") UUID sessionId,
            @Valid @RequestBody AdminActionRequest request) {
        return action(
                adminUserService.revokeSession(
                        SecurityUtils.getCurrentUserId(), userId, sessionId, request));
    }

    /** Changes one account's role for the authenticated administrator. */
    @Override
    @PatchMapping(ApiConstants.Admin.USER_ROLE)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AdminActionResponse>> changeRole(
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody AdminRoleChangeRequest request) {
        return action(
                adminUserService.changeRole(SecurityUtils.getCurrentUserId(), userId, request));
    }

    private ResponseEntity<ApiResponse<AdminActionResponse>> action(AdminActionResponse response) {
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK, response));
    }

    private ResponseEntity<ApiResponse<CursorPageResponse<AdminUserListItemResponse>>> page(
            CursorPageResponse<AdminUserListItemResponse> response) {
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK, response));
    }
}
