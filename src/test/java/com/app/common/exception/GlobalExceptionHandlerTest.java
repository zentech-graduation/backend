package com.app.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    @Test
    @SuppressWarnings("unchecked")
    void constraintViolationException_returnsValidationErrorWithFieldPaths() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("email");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must not be blank");

        ResponseEntity<ApiResponse<?>> response =
                handler.handleConstraintViolation(
                        new ConstraintViolationException(Set.of(violation)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
        Map<String, String> data = (Map<String, String>) response.getBody().getData();
        assertThat(data).containsEntry("email", "must not be blank");
    }

    @Test
    void authenticationException_returnsUnauthorized() {
        ResponseEntity<ApiResponse<?>> response =
                handler.handleAuthentication(new BadCredentialsException("bad credentials"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getCode()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void noResourceFoundException_returnsNotFound() {
        ResponseEntity<ApiResponse<?>> response =
                handler.handleNoResource(
                        new NoResourceFoundException(HttpMethod.GET, "/missing", "missing"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getCode()).isEqualTo("NOT_FOUND");
    }

    @Test
    void httpMethodNotSupportedException_returnsBadRequest() {
        ResponseEntity<ApiResponse<?>> response =
                handler.handleMethodNotSupported(
                        new HttpRequestMethodNotSupportedException("DELETE"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().getMessage()).contains("DELETE");
    }

    @Test
    void methodArgumentTypeMismatchException_returnsBadRequest() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("userId");

        ResponseEntity<ApiResponse<?>> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void dataIntegrityViolationException_returnsConflict() {
        ResponseEntity<ApiResponse<?>> response =
                handler.handleDataIntegrityViolation(
                        new DataIntegrityViolationException(
                                "duplicate key", new RuntimeException("duplicate key value")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getCode()).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().getMessage()).contains("conflicts");
    }
}
