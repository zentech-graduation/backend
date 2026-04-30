package com.app.common.exception;

import java.time.OffsetDateTime;

/**
 * Uniform error envelope returned by {@link GlobalExceptionHandler}.
 *
 * @param status HTTP status code
 * @param error short machine-readable error code (typically the HTTP status reason phrase)
 * @param message human-readable description; safe for client display
 * @param timestamp instant the error response was constructed (server time, UTC offset)
 */
public record ErrorResponse(int status, String error, String message, OffsetDateTime timestamp) {

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, OffsetDateTime.now());
    }
}
