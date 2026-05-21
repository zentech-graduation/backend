package com.app.modules.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.app.common.ApiConstants;
import com.app.common.response.ApiResponse;
import com.app.modules.auth.dto.request.ForgotPasswordRequest;
import com.app.modules.auth.dto.request.LoginRequest;
import com.app.modules.auth.dto.request.RefreshRequest;
import com.app.modules.auth.dto.request.RegisterRequest;
import com.app.modules.auth.dto.request.ResendVerificationRequest;
import com.app.modules.auth.dto.request.ResetPasswordRequest;
import com.app.modules.auth.dto.response.AuthResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** OpenAPI contract for the authentication module. */
@Tag(
        name = "Authentication",
        description = "Registration, login, token management, and password flows")
@RequestMapping(ApiConstants.Auth.ROOT)
public interface AuthApi {

    @Operation(
            summary = "Register a new user",
            description =
                    "Creates a user account and dispatches a verification email. No tokens are"
                            + " issued — the client must call /verify-email before logging in.",
            security = {})
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "Account created — verification email dispatched",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Username or email already in use",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "422",
                description = "Validation failure",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping(ApiConstants.Auth.REGISTER)
    ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request);

    /** Authenticates an existing user and returns access + refresh tokens. */
    @Operation(
            summary = "Log in",
            description = "Authenticates credentials and returns an access/refresh token pair.",
            security = {})
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Authenticated",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = AuthResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Invalid credentials",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "422",
                description = "Validation failure",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping(ApiConstants.Auth.LOGIN)
    ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest);

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
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = AuthResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Refresh token invalid or expired",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping(ApiConstants.Auth.REFRESH)
    ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest);

    /** Revokes the supplied refresh token. Idempotent. */
    @Operation(
            summary = "Log out",
            description =
                    "Revokes the supplied refresh token and blacklists the current access token."
                            + " Idempotent.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "204",
                description = "Logged out"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Missing or invalid access token",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping(ApiConstants.Auth.LOGOUT)
    ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshRequest request);

    /** Verifies an email address using the token embedded in the verification link. */
    @Operation(
            summary = "Verify email address",
            description =
                    "Marks the account's email as verified using the one-time token from the"
                            + " verification link, then issues a session pair so the user is"
                            + " logged in immediately.",
            security = {})
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Email verified — access + refresh tokens returned",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = AuthResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Token invalid or expired",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping(ApiConstants.Auth.VERIFY_EMAIL)
    ResponseEntity<ApiResponse<AuthResponse>> verifyEmail(
            @Parameter(
                            description =
                                    "One-time email verification token from the verification link",
                            required = true)
                    @RequestParam("token")
                    String token,
            HttpServletRequest httpRequest);

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
                description = "Email dispatched (or silently ignored if address unknown)",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping(ApiConstants.Auth.RESEND_VERIFY)
    ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request);

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
                description = "Reset email dispatched (or silently ignored if address unknown)",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping(ApiConstants.Auth.FORGOT_PASSWORD)
    ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request);

    /** Consumes a password-reset token and replaces the user's password hash. */
    @Operation(
            summary = "Reset password",
            description =
                    "Consumes the one-time reset token and replaces the account's password hash.",
            security = {})
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Password updated",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Token invalid or expired",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "422",
                description = "Validation failure",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping(ApiConstants.Auth.RESET_PASSWORD)
    ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request);
}
