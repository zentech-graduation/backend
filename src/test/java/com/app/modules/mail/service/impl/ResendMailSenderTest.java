package com.app.modules.mail.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Covers the provider-failure classification only.
 *
 * <p>The rest of {@link ResendMailSender} is a thin call into the vendor SDK and is exercised by
 * the consumer tests and the RabbitMQ integration test; this class pins the one piece of real
 * logic, because it is a message parse and message formats drift.
 */
class ResendMailSenderTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Failed to send email: 422 {\"statusCode\":422,\"name\":\"validation_error\"}",
                "Failed to send email: 400 {\"statusCode\":400}",
                "Failed to send email: 401 {\"statusCode\":401}",
                "Failed to send email: 403 {\"statusCode\":403}",
                "Failed to send email: 404 {\"statusCode\":404}"
            })
    void isPermanentRejection_clientErrorTheProviderWillNotAccept_isPermanent(String message) {
        assertThat(ResendMailSender.isPermanentRejection(message)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                // The two 4xx the provider expects a client to retry.
                "Failed to send email: 408 {\"statusCode\":408}",
                "Failed to send email: 429 {\"statusCode\":429}",
                // Server-side failures are the provider being unable to accept the message now.
                "Failed to send email: 500 {\"statusCode\":500}",
                "Failed to send email: 502 {\"statusCode\":502}",
                "Failed to send email: 503 {\"statusCode\":503}"
            })
    void isPermanentRejection_retryableStatus_isTransient(String message) {
        assertThat(ResendMailSender.isPermanentRejection(message)).isFalse();
    }

    @Test
    void isPermanentRejection_noStatusInMessage_isTransient() {
        // Unparseable must fall to transient: retrying a message that would have succeeded is
        // recoverable, dropping one that would have succeeded is not.
        assertThat(ResendMailSender.isPermanentRejection("Connection reset by peer")).isFalse();
    }

    @Test
    void isPermanentRejection_nullMessage_isTransient() {
        assertThat(ResendMailSender.isPermanentRejection(null)).isFalse();
    }

    @Test
    void isPermanentRejection_threeDigitRunInsideAnIdentifier_isNotReadAsAStatus() {
        // The word boundaries exist so an id or a byte count cannot be mistaken for a status.
        assertThat(ResendMailSender.isPermanentRejection("sent id=a4022b size=45000")).isFalse();
    }
}
