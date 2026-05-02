package com.app.common.exception;

import lombok.Getter;

/**
 * Generic, transport-aware application exception thrown by service-layer code.
 *
 * <p>Carries the HTTP status code the global handler should emit and a stable, machine-readable
 * error code that the client can branch on. Modules supply their own error code constants — there
 * is intentionally no shared enum, to avoid coupling sibling modules through a common catalogue.
 */
@Getter
public class ApiException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;

    public ApiException(int statusCode, String errorCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public ApiException(int statusCode, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }
}
