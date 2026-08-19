package com.app.modules.admin.controller;

import java.time.OffsetDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.app.common.ApiConstants;
import com.app.common.base.BaseController;
import com.app.common.enums.ApiSuccessCode;
import com.app.common.response.ApiResponse;
import com.app.modules.admin.api.AdminStatsApi;
import com.app.modules.admin.dto.response.AdminStatsCurrentResponse;
import com.app.modules.admin.dto.response.AdminStatsTimeseriesResponse;
import com.app.modules.admin.service.AdminStatsService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

/**
 * REST endpoints for the administrative statistics surface.
 *
 * <p>The class-level {@code @PreAuthorize} is the role gate. These paths sit under {@code
 * /api/v1/admin/} but outside the {@code /api/v1/admin/users/**} sub-tree, so the matcher that
 * applies in {@code SecurityConfig} is the broader {@code /api/v1/admin/**} rule, which admits a
 * moderator. Platform-wide figures are an administrator's view, so the narrowing happens here.
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class AdminStatsController extends BaseController implements AdminStatsApi {

    private final AdminStatsService adminStatsService;

    public AdminStatsController(AdminStatsService adminStatsService) {
        this.adminStatsService = adminStatsService;
    }

    /** Returns the newest stored statistics snapshot. */
    @Override
    @GetMapping(ApiConstants.Admin.STATS_CURRENT)
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AdminStatsCurrentResponse>> getCurrentStats() {
        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.OK, adminStatsService.getCurrent()));
    }

    /** Returns one metric's stored series over a window. */
    @Override
    @GetMapping(ApiConstants.Admin.STATS_TIMESERIES)
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AdminStatsTimeseriesResponse>> getStatsTimeseries(
            @RequestParam(defaultValue = "registrations") String metric,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime to) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.OK, adminStatsService.getTimeseries(metric, from, to)));
    }
}
