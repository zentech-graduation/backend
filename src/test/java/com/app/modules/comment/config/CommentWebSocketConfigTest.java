package com.app.modules.comment.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.app.common.security.config.CorsProperties;

class CommentWebSocketConfigTest {

    @Test
    void allowedOrigins_blankProperty_returnsEmptyArray() {
        CommentWebSocketConfig config = new CommentWebSocketConfig(null, new CorsProperties(""));

        assertThat(config.allowedOrigins()).isEmpty();
    }

    @Test
    void allowedOrigins_configuredOrigins_returnsParsedArray() {
        CommentWebSocketConfig config =
                new CommentWebSocketConfig(
                        null, new CorsProperties("https://example.com, https://app.example.com"));

        assertThat(config.allowedOrigins())
                .containsExactly("https://example.com", "https://app.example.com");
    }
}
