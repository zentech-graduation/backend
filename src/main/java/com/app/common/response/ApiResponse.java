package com.app.common.response;

import java.time.Instant;

import com.app.common.enums.ApiErrorCode;
import com.app.common.enums.ApiSuccessCode;
import org.springframework.util.StringUtils;

import com.app.common.exception.ApiException;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Uniform response envelope returned by every controller and the global exception handler.
 *
 * @param <T> payload type; {@code Void} for empty success or error envelopes
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private boolean success;
    private String code;
    private String message;
    private T data;

    @Builder.Default private Instant timestamp = Instant.now();

    /**
     * Success response with data, using the default message from the success code.
     *
     * @param successCode the success code
     * @param data response payload
     * @return success envelope
     */
    public static <T> ApiResponse<T> success(ApiSuccessCode successCode, T data) {
        return success(successCode, null, data);
    }

    /**
     * Success response without data.
     *
     * @param successCode the success code
     * @return success envelope
     */
    public static <T> ApiResponse<T> success(ApiSuccessCode successCode) {
        return success(successCode, null, null);
    }

    /**
     * Success response with a custom message and data.
     *
     * @param successCode the success code
     * @param message overrides the default message when non-blank
     * @param data response payload
     * @return success envelope
     */
    public static <T> ApiResponse<T> success(ApiSuccessCode successCode, String message, T data) {
        String resolved = StringUtils.hasText(message) ? message : successCode.getDefaultMessage();
        return ApiResponse.<T>builder()
                .success(true)
                .code(successCode.getCode())
                .message(resolved)
                .data(data)
                .build();
    }

    /**
     * Failure response with a custom message and optional error details payload.
     *
     * @param errorCode the error code
     * @param message overrides the default message when non-blank
     * @param data optional error details
     * @return failure envelope
     */
    public static <T> ApiResponse<T> failure(ApiErrorCode errorCode, String message, T data) {
        String resolved = StringUtils.hasText(message) ? message : errorCode.getDefaultMessage();
        return ApiResponse.<T>builder()
                .success(false)
                .code(errorCode.getCode())
                .message(resolved)
                .data(data)
                .build();
    }

    /**
     * Failure response using the error code's default message, without data.
     *
     * @param errorCode the error code
     * @return failure envelope
     */
    public static <T> ApiResponse<T> failure(ApiErrorCode errorCode) {
        return failure(errorCode, errorCode.getDefaultMessage(), null);
    }

    // --- Legacy factory methods retained for backward compatibility ---

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .code("OK")
                .message("Success")
                .data(data)
                .build();
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> created(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .code("CREATED")
                .message("Created")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(int httpStatus, String errorCode, String message) {
        return ApiResponse.<T>builder().success(false).code(errorCode).message(message).build();
    }

    public static ApiResponse<Void> error(ApiException ex) {
        return error(ex.getStatusCode(), ex.getErrorCode(), ex.getMessage());
    }
}
