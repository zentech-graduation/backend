package com.app.modules.notification.controller;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.app.common.base.BaseController;
import com.app.common.enums.ApiSuccessCode;
import com.app.common.response.ApiResponse;
import com.app.common.response.CursorPageResponse;
import com.app.common.security.user.UserPrincipal;
import com.app.common.security.util.SecurityUtils;
import com.app.modules.notification.api.NotificationApi;
import com.app.modules.notification.dto.response.NotificationResponse;
import com.app.modules.notification.dto.response.UnreadCountResponse;
import com.app.modules.notification.service.NotificationService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

/** REST endpoints for in-app notification management; requires an authenticated bearer token. */
@RestController
public class NotificationController extends BaseController implements NotificationApi {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    @GetMapping
    @RateLimiter(name = "highTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<CursorPageResponse<NotificationResponse>>> listNotifications(
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.OK,
                        notificationService.listNotifications(userId, cursor, limit)));
    }

    @Override
    @PatchMapping("/{notificationId}/read")
    @RateLimiter(name = "highTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable UUID notificationId, @AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markAsRead(notificationId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK));
    }

    @Override
    @PatchMapping("/read-all")
    @RateLimiter(name = "highTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markAllAsRead(principal.userId());
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK));
    }

    @Override
    @GetMapping("/unread-count")
    @RateLimiter(name = "highTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
            @AuthenticationPrincipal UserPrincipal principal) {
        long count = notificationService.getUnreadCount(principal.userId());
        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.OK, new UnreadCountResponse(count)));
    }
}
