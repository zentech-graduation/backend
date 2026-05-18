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
import com.app.common.base.BaseController;
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

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** HTTP surface for email + password authentication flows. */
@RestController
@RequestMapping(ApiConstants.Auth.ROOT)
@Tag(
        name = "Authentication",
        description = "Registration, login, token management, and password flows")
public class AuthController extends BaseController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registers a new user, dispatches verification + welcome emails asynchronously, and returns
     * the first session pair.
     */
    @Operation(
            summary = "Register a new user",
            description =
                    "Creates a user account, sends a verification email asynchronously, and returns"
                            + " an initial access/refresh token pair.",
            security = {})
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "Account created",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Username or email already in use",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "422",
                description = "Validation failure",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping(ApiConstants.Auth.REGISTER)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        AuthResponse body = authService.register(request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ApiSuccessCode.CREATED, body));
    }

    /** Authenticates an existing user and returns access + refresh tokens. */
    @Operation(
            summary = "Log in",
            description = "Authenticates credentials and returns an access/refresh token pair.",
            security = {})
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Authenticated",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Invalid credentials",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Email address not yet verified (AUTH_ACCOUNT_INACTIVE)",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "422",
                description = "Validation failure",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping(ApiConstants.Auth.LOGIN)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        AuthResponse body = authService.login(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK, body));
    }

    /** Rotates the refresh token and returns a new session pair. */
    @Operation(
            summary = "Refresh tokens",
            description =
                    "Rotates the supplied refresh token and returns a new access/refresh token"
                            + " pair.",
            security = {})
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Tokens rotated",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Refresh token invalid or expired",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description =
                        "Account is banned (AUTH_ACCOUNT_LOCKED) or suspended/deactivated"
                                + " (AUTH_ACCOUNT_INACTIVE); issued token has been revoked",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping(ApiConstants.Auth.REFRESH)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {
        AuthResponse body = authService.refresh(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK, body));
    }

    /** Revokes the supplied refresh token. Idempotent. */
    @Operation(
            summary = "Log out",
            description =
                    "Revokes the supplied refresh token and blacklists the current access token."
                            + " Idempotent.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "204",
                description = "Logged out"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Missing or invalid access token",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping(ApiConstants.Auth.LOGOUT)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .body(ApiResponse.success(ApiSuccessCode.NO_CONTENT));
    }

    /** Verifies an email address using the token embedded in the verification link. */
    @Operation(
            summary = "Verify email address",
            description =
                    "Marks the account's email as verified using the one-time token from the"
                            + " verification link.",
            security = {})
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Email verified"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Token invalid or expired",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping(ApiConstants.Auth.VERIFY_EMAIL)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @Parameter(
                            description =
                                    "One-time email verification token from the verification link",
                            required = true)
                    @RequestParam("token")
                    String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK));
    }

    /**
     * Re-issues a verification email when an account exists for the supplied address. Always
     * returns 200 to prevent account enumeration.
     */
    @Operation(
            summary = "Resend verification email",
            description =
                    "Re-sends the email verification link. Always returns 200 to prevent account"
                            + " enumeration.",
            security = {})
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Email dispatched (or silently ignored if address unknown)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping(ApiConstants.Auth.RESEND_VERIFY)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.email());
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK));
    }

    /**
     * Triggers a password-reset email when an account exists. Always returns 200 to prevent account
     * enumeration.
     */
    @Operation(
            summary = "Request password reset",
            description =
                    "Sends a password-reset link to the supplied email. Always returns 200 to"
                            + " prevent account enumeration.",
            security = {})
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Reset email dispatched (or silently ignored if address unknown)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping(ApiConstants.Auth.FORGOT_PASSWORD)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK));
    }

    /** Consumes a password-reset token and replaces the user's password hash. */
    @Operation(
            summary = "Reset password",
            description =
                    "Consumes the one-time reset token and replaces the account's password hash.",
            security = {})
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Password updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description =
                        "Token invalid, expired, or already consumed (AUTH_RESET_TOKEN_INVALID)",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description =
                        "Account is banned (AUTH_ACCOUNT_LOCKED) or suspended/deactivated"
                                + " (AUTH_ACCOUNT_INACTIVE)",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "422",
                description = "Validation failure",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping(ApiConstants.Auth.RESET_PASSWORD)
    @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK));
    }
}
