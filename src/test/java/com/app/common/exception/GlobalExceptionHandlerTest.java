package com.app.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void tokenNotFound_mapsTo404() {
        ResponseEntity<ErrorResponse> response =
                handler.handleTokenNotFound(new TokenNotFoundException("missing"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().error()).isEqualTo("Not Found");
        assertThat(response.getBody().message()).isEqualTo("missing");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void tokenExpired_mapsTo410() {
        ResponseEntity<ErrorResponse> response =
                handler.handleTokenExpired(new TokenExpiredException("expired"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getBody().status()).isEqualTo(410);
        assertThat(response.getBody().error()).isEqualTo("Gone");
        assertThat(response.getBody().message()).isEqualTo("expired");
    }

    @Test
    void tokenAlreadyUsed_mapsTo409() {
        ResponseEntity<ErrorResponse> response =
                handler.handleTokenAlreadyUsed(new TokenAlreadyUsedException("used"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().error()).isEqualTo("Conflict");
        assertThat(response.getBody().message()).isEqualTo("used");
    }

    @Test
    void mailSend_mapsTo502() {
        ResponseEntity<ErrorResponse> response =
                handler.handleMailSend(new MailSendException("upstream", new RuntimeException()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().status()).isEqualTo(502);
        assertThat(response.getBody().error()).isEqualTo("Bad Gateway");
        assertThat(response.getBody().message()).isEqualTo("upstream");
    }
}
