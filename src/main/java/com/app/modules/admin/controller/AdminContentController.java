package com.app.modules.admin.controller;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.app.common.ApiConstants;
import com.app.common.base.BaseController;
import com.app.common.enums.ApiSuccessCode;
import com.app.common.response.ApiResponse;
import com.app.common.response.CursorPageResponse;
import com.app.common.security.util.SecurityUtils;
import com.app.common.web.StrictQueryParameters;
import com.app.modules.admin.api.AdminContentApi;
import com.app.modules.admin.dto.response.AdminCommentSummaryResponse;
import com.app.modules.admin.dto.response.AdminPostSummaryResponse;
import com.app.modules.admin.dto.response.AdminReportTargetResponse;
import com.app.modules.admin.service.AdminContentService;
import com.app.modules.report.enums.ReportType;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

/**
 * REST endpoints for the administrative content-inspection surface.
 *
 * <p>These paths sit under {@code /api/v1/admin/} but outside the {@code /api/v1/admin/users/**}
 * sub-tree, so the matcher that applies in {@code SecurityConfig} is the broader {@code
 * /api/v1/admin/**} rule, which admits a moderator. That is the intended audience: reviewing an
 * account's content is moderator work, not administrator-only work. The class-level
 * {@code @PreAuthorize} states the same requirement rather than leaving it to the matcher alone, so
 * a change to either gate cannot silently open the surface.
 */
@RestController
@PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
public class AdminContentController extends BaseController implements AdminContentApi {

    private final AdminContentService adminContentService;

    public AdminContentController(AdminContentService adminContentService) {
        this.adminContentService = adminContentService;
    }

    /** Lists one account's posts for the authenticated moderator or administrator. */
    @Override
    @GetMapping(ApiConstants.Admin.CONTENT_POSTS_FOR_USER)
    @StrictQueryParameters
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<CursorPageResponse<AdminPostSummaryResponse>>>
            listPostsForUser(
                    @PathVariable("userId") UUID userId,
                    @RequestParam(required = false) String cursor,
                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.OK,
                        adminContentService.listPostsForUser(
                                SecurityUtils.getCurrentUserId(), userId, cursor, limit)));
    }

    /** Lists one account's comments for the authenticated moderator or administrator. */
    @Override
    @GetMapping(ApiConstants.Admin.CONTENT_COMMENTS_FOR_USER)
    @StrictQueryParameters
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<CursorPageResponse<AdminCommentSummaryResponse>>>
            listCommentsForUser(
                    @PathVariable("userId") UUID userId,
                    @RequestParam(required = false) String cursor,
                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.OK,
                        adminContentService.listCommentsForUser(
                                SecurityUtils.getCurrentUserId(), userId, cursor, limit)));
    }

    /** Reads one entity by identifier for the authenticated moderator or administrator. */
    @Override
    @GetMapping(ApiConstants.Admin.CONTENT_ENTITY)
    @StrictQueryParameters
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AdminReportTargetResponse>> getEntity(
            @PathVariable("entityType") ReportType entityType,
            @PathVariable("entityId") UUID entityId) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.OK,
                        adminContentService.getEntity(
                                SecurityUtils.getCurrentUserId(), entityType, entityId)));
    }
}
