package com.app.common.enums;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/** Machine-readable error codes for all API failure responses. */
@Getter
public enum ApiErrorCode {
    // spotless:off

	// Common
	VALIDATION_ERROR("VALIDATION_ERROR", "Request validation failed", HttpStatus.BAD_REQUEST),
	BAD_REQUEST("BAD_REQUEST", "Invalid request", HttpStatus.BAD_REQUEST),
	NOT_FOUND("NOT_FOUND", "Requested resource was not found", HttpStatus.NOT_FOUND),
	FORBIDDEN("FORBIDDEN", "Access to this resource is forbidden", HttpStatus.FORBIDDEN),
	UNAUTHORIZED("UNAUTHORIZED", "Authentication is required", HttpStatus.UNAUTHORIZED),
	INTERNAL_ERROR("INTERNAL_ERROR", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR),
	TOO_MANY_REQUESTS("TOO_MANY_REQUESTS", "The system is busy. Please try again in a few minutes.", HttpStatus.TOO_MANY_REQUESTS),
	SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", "External service temporarily unavailable. Please try again later.", HttpStatus.SERVICE_UNAVAILABLE),

	// Auth
	AUTH_INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", "Invalid email or password", HttpStatus.UNAUTHORIZED),
	AUTH_TOKEN_EXPIRED("AUTH_TOKEN_EXPIRED", "Access token has expired", HttpStatus.UNAUTHORIZED),
	AUTH_TOKEN_INVALID("AUTH_TOKEN_INVALID", "Invalid access token", HttpStatus.UNAUTHORIZED),
	AUTH_REFRESH_TOKEN_EXPIRED("AUTH_REFRESH_TOKEN_EXPIRED", "Refresh token has expired", HttpStatus.UNAUTHORIZED),
	AUTH_REFRESH_TOKEN_INVALID("AUTH_REFRESH_TOKEN_INVALID", "Invalid or revoked refresh token", HttpStatus.UNAUTHORIZED),
	AUTH_ACCOUNT_LOCKED("AUTH_ACCOUNT_LOCKED", "Account is temporarily locked due to too many failed attempts", HttpStatus.FORBIDDEN),
	AUTH_ACCOUNT_INACTIVE("AUTH_ACCOUNT_INACTIVE", "Account is inactive", HttpStatus.FORBIDDEN),
	AUTH_EMAIL_NOT_VERIFIED("AUTH_EMAIL_NOT_VERIFIED", "Email address has not been verified", HttpStatus.FORBIDDEN),
	AUTH_PASSWORD_MISMATCH("AUTH_PASSWORD_MISMATCH", "Current password is incorrect", HttpStatus.BAD_REQUEST),
	AUTH_RESET_TOKEN_INVALID("AUTH_RESET_TOKEN_INVALID", "Invalid or expired reset token", HttpStatus.BAD_REQUEST),
	AUTH_RESET_TOKEN_EXPIRED("AUTH_RESET_TOKEN_EXPIRED", "Reset token has expired", HttpStatus.GONE),
	AUTH_RESET_TOKEN_USED("AUTH_RESET_TOKEN_USED", "Reset token has already been used", HttpStatus.CONFLICT),
	AUTH_VERIFY_TOKEN_INVALID("AUTH_VERIFY_TOKEN_INVALID", "Email verification token is invalid or has expired", HttpStatus.BAD_REQUEST),
	AUTH_OAUTH2_EXCHANGE_CODE_INVALID("AUTH_OAUTH2_EXCHANGE_CODE_INVALID", "OAuth2 exchange code is invalid or has expired", HttpStatus.BAD_REQUEST),

	// Users
	USER_EMAIL_ALREADY_EXISTS("USER_EMAIL_ALREADY_EXISTS", "An account with this email already exists", HttpStatus.CONFLICT),
	USER_USERNAME_ALREADY_EXISTS("USER_USERNAME_ALREADY_EXISTS", "Username is already taken", HttpStatus.CONFLICT),

<<<<<<< HEAD
    // Social
    SOCIAL_SELF_FOLLOW(
            "SOCIAL_SELF_FOLLOW",
            "You cannot follow yourself",
            HttpStatus.BAD_REQUEST),

    SOCIAL_ALREADY_FOLLOWING(
            "SOCIAL_ALREADY_FOLLOWING",
            "You are already following this user",
            HttpStatus.CONFLICT),

    SOCIAL_ALREADY_REQUESTED(
            "SOCIAL_ALREADY_REQUESTED",
            "Follow request already sent",
            HttpStatus.CONFLICT),

    SOCIAL_REQUEST_NOT_FOUND(
            "SOCIAL_REQUEST_NOT_FOUND",
            "Follow request not found",
            HttpStatus.NOT_FOUND),

    SOCIAL_SELF_BLOCK(
            "SOCIAL_SELF_BLOCK",
            "You cannot block yourself",
            HttpStatus.BAD_REQUEST),

    SOCIAL_ALREADY_BLOCKED(
            "SOCIAL_ALREADY_BLOCKED",
            "User already blocked",
            HttpStatus.CONFLICT),

    SOCIAL_BLOCKED(
            "SOCIAL_BLOCKED",
            "This action is not allowed because of a block relationship",
            HttpStatus.FORBIDDEN);
=======
	// Social
	SOCIAL_SELF_FOLLOW_NOT_ALLOWED("SOCIAL_SELF_FOLLOW_NOT_ALLOWED", "You cannot follow yourself", HttpStatus.BAD_REQUEST),
	SOCIAL_FOLLOW_ALREADY_EXISTS("SOCIAL_FOLLOW_ALREADY_EXISTS", "Follow relationship already exists", HttpStatus.CONFLICT),
	SOCIAL_FOLLOW_BLOCKED("SOCIAL_FOLLOW_BLOCKED", "Follow is not allowed because a block relationship exists", HttpStatus.FORBIDDEN),
	// Media
	MEDIA_INVALID_METADATA("MEDIA_INVALID_METADATA", "Media metadata is invalid", HttpStatus.BAD_REQUEST),
	MEDIA_STORAGE_KEY_ALREADY_EXISTS("MEDIA_STORAGE_KEY_ALREADY_EXISTS", "Media storage key already exists", HttpStatus.CONFLICT),
	MEDIA_CDN_NOT_CONFIGURED("MEDIA_CDN_NOT_CONFIGURED", "Media CDN is not configured", HttpStatus.SERVICE_UNAVAILABLE),
	MEDIA_STORAGE_NOT_CONFIGURED("MEDIA_STORAGE_NOT_CONFIGURED", "Media object storage is not configured", HttpStatus.SERVICE_UNAVAILABLE),
	MEDIA_UPLOAD_URL_FAILED("MEDIA_UPLOAD_URL_FAILED", "Media upload URL could not be generated", HttpStatus.SERVICE_UNAVAILABLE);
>>>>>>> 9173792e58046801ceb05ea721b53d5202d8ba28

	// spotless:on

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    ApiErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }
}
