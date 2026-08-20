package com.app.common.exception;

import org.springframework.http.HttpStatus;

import com.app.common.enums.ApiErrorCode;

import lombok.Getter;

/**
 * Application exception backed by a typed {@link ApiErrorCode}, carrying HTTP status and a
 * machine-readable error code.
 *
 * <p>May also carry a structured payload, rendered as the {@code data} member of the failure
 * envelope. This is for a failure a client has to act on field by field rather than merely report,
 * which is why validation failures already answer that shape. It is server-derived detail about the
 * rejection, never an echo of the request, and it must stay free of anything the caller was not
 * already entitled to know.
 */
@Getter
public class AppException extends RuntimeException {

    private final ApiErrorCode errorCode;

    /**
     * Structured detail rendered as the failure envelope's {@code data}; null when there is none.
     */
    private final Object details;

    public AppException(ApiErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), null);
    }

    public AppException(ApiErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public AppException(ApiErrorCode errorCode, Object details) {
        this(errorCode, errorCode.getDefaultMessage(), details);
    }

    public AppException(ApiErrorCode errorCode, String message, Object details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    /**
     * Returns the HTTP status derived from the error code.
     *
     * @return HTTP status for this exception
     */
    public HttpStatus getHttpStatus() {
        return errorCode.getHttpStatus();
    }
}
