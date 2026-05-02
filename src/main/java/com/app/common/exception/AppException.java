package com.app.common.exception;

import org.springframework.http.HttpStatus;

import com.app.common.enums.ApiErrorCode;

import lombok.Getter;

/**
 * Application exception backed by a typed {@link ApiErrorCode}, carrying HTTP status and a
 * machine-readable error code.
 */
@Getter
public class AppException extends RuntimeException {

    private final ApiErrorCode errorCode;

    public AppException(ApiErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public AppException(ApiErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
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
