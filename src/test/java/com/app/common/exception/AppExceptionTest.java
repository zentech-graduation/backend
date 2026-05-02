package com.app.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.app.common.enums.ApiErrorCode;

class AppExceptionTest {

    @Test
    void constructor_withErrorCodeOnly_usesDefaultMessage() {
        AppException ex = new AppException(ApiErrorCode.NOT_FOUND);

        assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo(ApiErrorCode.NOT_FOUND.getDefaultMessage());
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void constructor_withCustomMessage_usesProvidedMessage() {
        AppException ex = new AppException(ApiErrorCode.FORBIDDEN, "custom message");

        assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.FORBIDDEN);
        assertThat(ex.getMessage()).isEqualTo("custom message");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getHttpStatus_delegatesToErrorCode() {
        AppException ex = new AppException(ApiErrorCode.UNAUTHORIZED);

        assertThat(ex.getHttpStatus()).isEqualTo(ApiErrorCode.UNAUTHORIZED.getHttpStatus());
    }

    @Test
    void getHttpStatus_authTokenExpired_returnsUnauthorized() {
        AppException ex = new AppException(ApiErrorCode.AUTH_TOKEN_EXPIRED);

        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getHttpStatus_accountLocked_returnsForbidden() {
        AppException ex = new AppException(ApiErrorCode.AUTH_ACCOUNT_LOCKED);

        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
