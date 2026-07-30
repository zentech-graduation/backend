package com.app.modules.users.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.app.common.ApiConstants;
import com.app.common.base.BaseController;
import com.app.common.enums.ApiSuccessCode;
import com.app.common.response.ApiResponse;
import com.app.common.security.user.UserPrincipal;
import com.app.common.security.util.SecurityUtils;
import com.app.modules.users.api.UserApi;
import com.app.modules.users.dto.request.UpdateProfileRequest;
import com.app.modules.users.dto.request.UpdateSettingsRequest;
import com.app.modules.users.dto.response.PublicUserProfileResponse;
import com.app.modules.users.dto.response.UserProfileResponse;
import com.app.modules.users.dto.response.UserSettingsResponse;
import com.app.modules.users.service.UserService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

/** REST endpoints for user profile and settings management. */
@RestController
public class UserController extends BaseController implements UserApi {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** Returns the full profile of the authenticated user. */
    @Override
    @GetMapping(ApiConstants.Users.ME)
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.OK, userService.getMyProfile(userId)));
    }

    /** Applies partial profile updates for the authenticated user. */
    @Override
    @PatchMapping(ApiConstants.Users.ME)
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.OK, userService.updateMyProfile(userId, request)));
    }

    /** Returns the public profile of the specified user. */
    @Override
    @GetMapping(ApiConstants.Users.BY_ID)
    @RateLimiter(name = "highTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<PublicUserProfileResponse>> getUserProfile(
            @PathVariable UUID userId, @AuthenticationPrincipal UserPrincipal principal) {
        UUID viewerId = principal != null ? principal.userId() : null;
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.OK, userService.getUserProfile(viewerId, userId)));
    }

    /**
     * Returns the public profile of the user holding the given username, matched case-sensitively.
     */
    @Override
    @GetMapping(ApiConstants.Users.BY_USERNAME)
    @RateLimiter(name = "highTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<PublicUserProfileResponse>> getUserProfileByUsername(
            @PathVariable String username, @AuthenticationPrincipal UserPrincipal principal) {
        UUID viewerId = principal != null ? principal.userId() : null;
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.OK,
                        userService.getUserProfileByUsername(viewerId, username)));
    }

    /** Returns the notification and privacy settings of the authenticated user. */
    @Override
    @GetMapping(ApiConstants.Users.ME_SETTINGS)
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<UserSettingsResponse>> getMySettings() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.OK, userService.getMySettings(userId)));
    }

    /** Applies partial settings updates for the authenticated user. */
    @Override
    @PatchMapping(ApiConstants.Users.ME_SETTINGS)
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<UserSettingsResponse>> updateMySettings(
            @Valid @RequestBody UpdateSettingsRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.OK, userService.updateMySettings(userId, request)));
    }
}
