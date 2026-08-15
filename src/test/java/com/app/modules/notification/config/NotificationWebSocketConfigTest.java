package com.app.modules.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.app.common.security.config.CorsProperties;

class NotificationWebSocketConfigTest {

    @Test
    void allowedOrigins_blankProperty_returnsEmptyArray() {
        NotificationWebSocketConfig config =
                new NotificationWebSocketConfig(null, new CorsProperties(""));

        assertThat(config.allowedOrigins()).isEmpty();
    }

    @Test
    void allowedOrigins_configuredOrigins_returnsParsedArray() {
        NotificationWebSocketConfig config =
                new NotificationWebSocketConfig(
                        null, new CorsProperties("https://example.com, https://app.example.com"));

        assertThat(config.allowedOrigins())
                .containsExactly("https://example.com", "https://app.example.com");
    }
}
