package com.app.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<?>> handleHandlerMethodValidation(
            HandlerMethodValidationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults()
                .forEach(
                        result ->
                                result.getResolvableErrors()
                                        .forEach(
                                                error ->
                                                        errors.put(
                                                                result.getMethodParameter()
                                                                        .getParameterName(),
                                                                error.getDefaultMessage())));
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

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        // Client input error, not a server fault: WARN without a stack trace keeps an
        // unauthenticated caller from flooding the ERROR log, and the offending value is never
        // echoed back.
        log.warn("Type mismatch on request parameter '{}'", ex.getName());
        return ResponseEntity.status(ApiErrorCode.BAD_REQUEST.getHttpStatus())
                .body(ApiResponse.failure(ApiErrorCode.BAD_REQUEST));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex) {
        // Client input error, not a server fault: WARN without a stack trace.
        log.warn("Unsupported Content-Type: {}", ex.getContentType());
        return ResponseEntity.status(ApiErrorCode.UNSUPPORTED_MEDIA_TYPE.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.failure(ApiErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<?>> handleNotAcceptable(
            HttpMediaTypeNotAcceptableException ex) {
        // The Content-Type is set explicitly rather than negotiated: the caller's Accept header
        // is exactly what this handler exists to reject, so honouring it here would recreate the
        // failure the handler is supposed to resolve. Writing JSON regardless is what lets this
        // response reach the caller through the normal advice mechanism instead of falling
        // through to Spring's response.sendError(...), which re-enters the security filter chain
        // on the /error ERROR dispatch and answers 401 instead of 406.
        log.warn("No acceptable media type for Accept header");
        return ResponseEntity.status(ApiErrorCode.NOT_ACCEPTABLE.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.failure(ApiErrorCode.NOT_ACCEPTABLE));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<?>> handleMissingParameter(
            MissingServletRequestParameterException ex) {
        // Client input error, not a server fault: WARN without a stack trace.
        log.warn("Missing required request parameter '{}'", ex.getParameterName());
        return ResponseEntity.status(ApiErrorCode.MISSING_REQUIRED_PARAMETER.getHttpStatus())
                .body(ApiResponse.failure(ApiErrorCode.MISSING_REQUIRED_PARAMETER));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleMalformedRequestBody(
            HttpMessageNotReadableException ex) {
        // Every cause this handler sees - an unrecognised property, invalid JSON syntax, a
        // missing body, a wrong root type, or an invalid enum value - reaches Spring MVC as this
        // one exception type, so one handler covers all of them. Client input error, not a server
        // fault: WARN without a stack trace, and never echo the cause's message, which names the
        // rejected property and the fully qualified target DTO and would otherwise hand an
        // unauthenticated caller a schema-enumeration oracle over every request DTO.
        Throwable cause = ex.getCause();
        log.warn(
                "Malformed request body, cause: {}",
                cause == null ? "none" : cause.getClass().getSimpleName());
        return ResponseEntity.status(ApiErrorCode.MALFORMED_REQUEST_BODY.getHttpStatus())
                .body(ApiResponse.failure(ApiErrorCode.MALFORMED_REQUEST_BODY));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {
        log.warn("Database constraint violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        ApiResponse.failure(
                                ApiErrorCode.BAD_REQUEST,
                                "The request conflicts with an existing resource",
                                null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleUnknown(Exception ex) {
        log.error("Unhandled exception reached global handler", ex);
        return ResponseEntity.status(ApiErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(ApiResponse.failure(ApiErrorCode.INTERNAL_ERROR));
    }
}
