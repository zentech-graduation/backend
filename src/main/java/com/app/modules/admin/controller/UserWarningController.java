package com.app.modules.admin.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.app.common.ApiConstants;
import com.app.common.base.BaseController;
import com.app.common.enums.ApiSuccessCode;
import com.app.common.response.ApiResponse;
import com.app.common.response.CursorPageResponse;
import com.app.common.security.util.SecurityUtils;
import com.app.modules.admin.api.UserWarningApi;
import com.app.modules.admin.dto.response.UserWarningResponse;
import com.app.modules.admin.service.UserDisciplineService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

/**
 * REST endpoint for an account reading its own warnings.
 *
 * <p>No role gate: any authenticated caller may read this. The account read is taken from the
 * security context and never from the request, so there is no parameter through which one account
 * could ask for another's warnings.
 */
@RestController
public class UserWarningController extends BaseController implements UserWarningApi {

    private final UserDisciplineService userDisciplineService;

    public UserWarningController(UserDisciplineService userDisciplineService) {
        this.userDisciplineService = userDisciplineService;
    }

    /** Returns a cursor page of the authenticated caller's own warnings. */
    @Override
    @GetMapping(ApiConstants.Users.ME_WARNINGS)
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<CursorPageResponse<UserWarningResponse>>> listOwnWarnings(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.OK,
                        userDisciplineService.listOwnWarnings(
                                SecurityUtils.getCurrentUserId(), cursor, limit)));
    }
}
