package com.app.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.app.common.enums.ApiErrorCode;
import com.app.common.response.ApiResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Translates framework-level and generic runtime exceptions into a uniform {@link ApiResponse}
 * envelope. Domain-specific exceptions must be converted to {@link AppException} at the service
 * layer before they reach this handler.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<?>> handleAppException(AppException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiResponse.failure(ex.getErrorCode(), ex.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(fe -> errors.put(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.status(ApiErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.failure(ApiErrorCode.VALIDATION_ERROR, null, errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleConstraintViolation(
            ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations()
                .forEach(
                        cv -> {
                            String path = cv.getPropertyPath().toString();
                            errors.put(path, cv.getMessage());
                        });
        return ResponseEntity.status(ApiErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.failure(ApiErrorCode.VALIDATION_ERROR, null, errors));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(ApiErrorCode.FORBIDDEN.getHttpStatus())
                .body(ApiResponse.failure(ApiErrorCode.FORBIDDEN));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<?>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(ApiErrorCode.UNAUTHORIZED.getHttpStatus())
                .body(ApiResponse.failure(ApiErrorCode.UNAUTHORIZED));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(ApiErrorCode.NOT_FOUND.getHttpStatus())
                .body(ApiResponse.failure(ApiErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        String message = "HTTP method not supported: " + ex.getMethod();
        return ResponseEntity.status(ApiErrorCode.BAD_REQUEST.getHttpStatus())
                .body(ApiResponse.failure(ApiErrorCode.BAD_REQUEST, message, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleUnknown(Exception ex) {
        log.error("Unhandled exception reached global handler", ex);
        return ResponseEntity.status(ApiErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(ApiResponse.failure(ApiErrorCode.INTERNAL_ERROR));
    }
}
