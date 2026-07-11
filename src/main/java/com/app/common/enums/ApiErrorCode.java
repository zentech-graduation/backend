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
	AUTH_PASSWORD_MISMATCH("AUTH_PASSWORD_MISMATCH", "Current password is incorrect", HttpStatus.BAD_REQUEST),
	AUTH_RESET_TOKEN_INVALID("AUTH_RESET_TOKEN_INVALID", "Invalid or expired reset token", HttpStatus.BAD_REQUEST),
	AUTH_RESET_TOKEN_EXPIRED("AUTH_RESET_TOKEN_EXPIRED", "Reset token has expired", HttpStatus.GONE),
	AUTH_RESET_TOKEN_USED("AUTH_RESET_TOKEN_USED", "Reset token has already been used", HttpStatus.CONFLICT),

	// Users
	USER_EMAIL_ALREADY_EXISTS("USER_EMAIL_ALREADY_EXISTS", "An account with this email already exists", HttpStatus.CONFLICT),
	USER_USERNAME_ALREADY_EXISTS("USER_USERNAME_ALREADY_EXISTS", "Username is already taken", HttpStatus.CONFLICT),

	// Reports
	REPORT_NOT_FOUND("REPORT_NOT_FOUND", "Report was not found", HttpStatus.NOT_FOUND),
	REPORT_TARGET_NOT_FOUND("REPORT_TARGET_NOT_FOUND", "Reported entity was not found", HttpStatus.NOT_FOUND),
	REPORT_DUPLICATE("REPORT_DUPLICATE", "An active report already exists for this entity", HttpStatus.CONFLICT),
	REPORT_INVALID_TRANSITION("REPORT_INVALID_TRANSITION", "Invalid report status transition", HttpStatus.CONFLICT);

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
