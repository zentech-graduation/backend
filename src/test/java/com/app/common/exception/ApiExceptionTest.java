package com.app.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiExceptionTest {

    @Test
    void constructor_withoutCause_storesStatusAndErrorCode() {
        ApiException ex = new ApiException(422, "VALIDATION_ERROR", "invalid input");

        assertThat(ex.getStatusCode()).isEqualTo(422);
        assertThat(ex.getErrorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(ex.getMessage()).isEqualTo("invalid input");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void constructor_withCause_storesAllFields() {
        RuntimeException cause = new RuntimeException("root");
        ApiException ex = new ApiException(500, "INTERNAL", "server error", cause);

        assertThat(ex.getStatusCode()).isEqualTo(500);
        assertThat(ex.getErrorCode()).isEqualTo("INTERNAL");
        assertThat(ex.getMessage()).isEqualTo("server error");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
