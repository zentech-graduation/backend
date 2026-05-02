package com.app.modules.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.app.common.ApiConstants;
import com.app.common.enums.ApiSuccessCode;
import com.app.common.response.ApiResponse;
import com.app.modules.auth.dto.request.ForgotPasswordRequest;
import com.app.modules.auth.dto.request.LoginRequest;
import com.app.modules.auth.dto.request.RefreshRequest;
import com.app.modules.auth.dto.request.RegisterRequest;
import com.app.modules.auth.dto.request.ResendVerificationRequest;
import com.app.modules.auth.dto.request.ResetPasswordRequest;
import com.app.modules.auth.dto.response.AuthResponse;
import com.app.modules.auth.service.AuthService;

/** HTTP surface for email + password authentication flows. */
@RestController
@RequestMapping(ApiConstants.Auth.ROOT)
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registers a new user, dispatches verification + welcome emails asynchronously, and returns
     * the first session pair.
     */
    @PostMapping(ApiConstants.Auth.REGISTER)
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        AuthResponse body = authService.register(request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ApiSuccessCode.CREATED, body));
    }

    /** Authenticates an existing user and returns access + refresh tokens. */
    @PostMapping(ApiConstants.Auth.LOGIN)
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        AuthResponse body = authService.login(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK, body));
    }

    /** Rotates the refresh token and returns a new session pair. */
    @PostMapping(ApiConstants.Auth.REFRESH)
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {
        AuthResponse body = authService.refresh(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK, body));
    }

    /** Revokes the supplied refresh token. Idempotent. */
    @PostMapping(ApiConstants.Auth.LOGOUT)
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .body(ApiResponse.success(ApiSuccessCode.NO_CONTENT));
    }

    /** Verifies an email address using the token embedded in the verification link. */
    @GetMapping(ApiConstants.Auth.VERIFY_EMAIL)
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam("token") String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK));
    }

    /**
     * Re-issues a verification email when an account exists for the supplied address. Always
     * returns 200 to prevent account enumeration.
     */
    @PostMapping(ApiConstants.Auth.RESEND_VERIFY)
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.email());
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK));
    }

    /**
     * Triggers a password-reset email when an account exists. Always returns 200 to prevent account
     * enumeration.
     */
    @PostMapping(ApiConstants.Auth.FORGOT_PASSWORD)
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK));
    }

    /** Consumes a password-reset token and replaces the user's password hash. */
    @PostMapping(ApiConstants.Auth.RESET_PASSWORD)
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK));
    }
}
