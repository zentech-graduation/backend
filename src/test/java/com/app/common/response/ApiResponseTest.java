package com.app.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.common.enums.ApiErrorCode;
import com.app.common.enums.ApiSuccessCode;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void success_withData_setsFieldsFromSuccessCode() {
        ApiResponse<String> response = ApiResponse.success(ApiSuccessCode.OK, "payload");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("OK");
        assertThat(response.getMessage()).isEqualTo(ApiSuccessCode.OK.getDefaultMessage());
        assertThat(response.getData()).isEqualTo("payload");
    }

    @Test
    void success_withoutData_setsNullData() {
        ApiResponse<Void> response = ApiResponse.success(ApiSuccessCode.NO_CONTENT);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("NO_CONTENT");
        assertThat(response.getData()).isNull();
    }

    @Test
    void success_withCustomMessage_overridesDefault() {
        ApiResponse<String> response =
                ApiResponse.success(ApiSuccessCode.OK, "custom message", "payload");

        assertThat(response.getMessage()).isEqualTo("custom message");
    }

    @Test
    void success_withNullMessage_fallsBackToDefault() {
        ApiResponse<String> response = ApiResponse.success(ApiSuccessCode.CREATED, null, "data");

        assertThat(response.getMessage()).isEqualTo(ApiSuccessCode.CREATED.getDefaultMessage());
    }

    @Test
    void success_withBlankMessage_fallsBackToDefault() {
        ApiResponse<String> response = ApiResponse.success(ApiSuccessCode.OK, "   ", "data");

        assertThat(response.getMessage()).isEqualTo(ApiSuccessCode.OK.getDefaultMessage());
    }

    @Test
    void failure_withErrorCode_setsFieldsFromErrorCode() {
        ApiResponse<Void> response = ApiResponse.failure(ApiErrorCode.NOT_FOUND);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo("NOT_FOUND");
        assertThat(response.getMessage()).isEqualTo(ApiErrorCode.NOT_FOUND.getDefaultMessage());
        assertThat(response.getData()).isNull();
    }

    @Test
    void failure_withCustomMessage_overridesDefault() {
        ApiResponse<Void> response =
                ApiResponse.failure(ApiErrorCode.BAD_REQUEST, "custom error", null);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo("BAD_REQUEST");
        assertThat(response.getMessage()).isEqualTo("custom error");
    }

    @Test
    void failure_withNullMessage_fallsBackToDefault() {
        ApiResponse<String> response = ApiResponse.failure(ApiErrorCode.UNAUTHORIZED, null, null);

        assertThat(response.getMessage()).isEqualTo(ApiErrorCode.UNAUTHORIZED.getDefaultMessage());
    }

    @Test
    void failure_withBlankMessage_fallsBackToDefault() {
        ApiResponse<String> response = ApiResponse.failure(ApiErrorCode.FORBIDDEN, "  ", null);

        assertThat(response.getMessage()).isEqualTo(ApiErrorCode.FORBIDDEN.getDefaultMessage());
    }

    @Test
    void failure_withData_setsDataField() {
        ApiResponse<String> response =
                ApiResponse.failure(ApiErrorCode.VALIDATION_ERROR, null, "error details");

        assertThat(response.getData()).isEqualTo("error details");
    }
}
