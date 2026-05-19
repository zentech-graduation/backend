package com.app.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import com.app.common.enums.ApiErrorCode;
import com.app.common.response.ApiResponse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void appException_propagatesHttpStatusAndErrorCode() {
        AppException ex = new AppException(ApiErrorCode.NOT_FOUND, "user not found");

        ResponseEntity<ApiResponse<?>> response = handler.handleAppException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().getMessage()).isEqualTo("user not found");
    }

    @Test
    void appException_usesDefaultMessageWhenNoCustomMessage() {
        AppException ex = new AppException(ApiErrorCode.FORBIDDEN);

        ResponseEntity<ApiResponse<?>> response = handler.handleAppException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessage())
                .isEqualTo(ApiErrorCode.FORBIDDEN.getDefaultMessage());
    }

    @Test
    void validationException_returnsValidationErrorCodeWithFieldErrorsMap() {
        FieldError emailError = new FieldError("obj", "email", "must be a well-formed email");
        FieldError nameError = new FieldError("obj", "username", "must not be blank");
        BindingResult binding = mock(BindingResult.class);
        when(binding.getFieldErrors()).thenReturn(List.of(emailError, nameError));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(binding);

        ResponseEntity<ApiResponse<?>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) response.getBody().getData();
        assertThat(data).containsEntry("email", "must be a well-formed email");
        assertThat(data).containsEntry("username", "must not be blank");
    }

    @Test
    void accessDeniedException_returnsForbidden() {
        ResponseEntity<ApiResponse<?>> response =
                handler.handleAccessDenied(new AccessDeniedException("forbidden"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo("FORBIDDEN");
    }

    @Test
    void unknownException_returnsInternalError() {
        ResponseEntity<ApiResponse<?>> response =
                handler.handleUnknown(new RuntimeException("unexpected"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_ERROR");
    }
}
