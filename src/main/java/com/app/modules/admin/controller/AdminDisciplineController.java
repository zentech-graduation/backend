package com.app.modules.admin.controller;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
import com.app.modules.admin.api.AdminDisciplineApi;
import com.app.modules.admin.dto.request.AdminActionRequest;
import com.app.modules.admin.dto.request.AdminWarnUserRequest;
import com.app.modules.admin.dto.response.AdminActionResponse;
import com.app.modules.admin.dto.response.AdminViolationResponse;
import com.app.modules.admin.dto.response.AdminWarnUserResponse;
import com.app.modules.admin.service.UserDisciplineService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

/**
 * REST endpoints for the warning and strike ladder.
 *
 * <p>The class-level gate matches the {@code /api/v1/admin/**} matcher in {@code SecurityConfig};
 * the two reversals narrow it to ADMIN per method. Both layers are declared because a service
 * reachable only through a path matcher stops being protected the moment someone adds a route that
 * does not fit the pattern.
 */
@RestController
@PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
public class AdminDisciplineController extends BaseController implements AdminDisciplineApi {

    private final UserDisciplineService userDisciplineService;

    public AdminDisciplineController(UserDisciplineService userDisciplineService) {
        this.userDisciplineService = userDisciplineService;
    }

    /** Issues one warning against an account for the authenticated moderator. */
    @Override
    @PostMapping(ApiConstants.Admin.WARN_USER)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AdminWarnUserResponse>> warnUser(
            @PathVariable("userId") UUID userId, @Valid @RequestBody AdminWarnUserRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.OK,
                        userDisciplineService.issueWarning(
                                SecurityUtils.getCurrentUserId(), userId, request)));
    }

    /** Returns a cursor page of the account's violation history, scoped to the caller's role. */
    @Override
    @GetMapping(ApiConstants.Admin.VIOLATIONS_FOR_USER)
    @StrictQueryParameters
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<CursorPageResponse<AdminViolationResponse>>> listViolations(
            @PathVariable("userId") UUID userId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.OK,
                        userDisciplineService.listViolations(
                                SecurityUtils.getCurrentUserId(), userId, cursor, limit)));
    }

    /** Revokes one warning for the authenticated administrator. */
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping(ApiConstants.Admin.REVOKE_WARNING)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AdminActionResponse>> revokeWarning(
            @PathVariable("warningId") UUID warningId,
            @Valid @RequestBody AdminActionRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.OK,
                        userDisciplineService.revokeWarning(
                                SecurityUtils.getCurrentUserId(), warningId, request)));
    }

    /** Revokes one strike for the authenticated administrator. */
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping(ApiConstants.Admin.REVOKE_STRIKE)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AdminActionResponse>> revokeStrike(
            @PathVariable("strikeId") UUID strikeId,
            @Valid @RequestBody AdminActionRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.OK,
                        userDisciplineService.revokeStrike(
                                SecurityUtils.getCurrentUserId(), strikeId, request)));
    }
}
