package com.app.common.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class CorsPropertiesTest {

    @Test
    void allowedOriginList_blank_returnsEmptyList() {
        assertThat(new CorsProperties("").allowedOriginList()).isEmpty();
    }

    @Test
    void allowedOriginList_null_returnsEmptyList() {
        assertThat(new CorsProperties(null).allowedOriginList()).isEmpty();
    }

    @Test
    void allowedOriginList_whitespace_returnsEmptyList() {
        assertThat(new CorsProperties("   ").allowedOriginList()).isEmpty();
    }

    @Test
    void allowedOriginList_commaSeparated_trimsEachOrigin() {
        List<String> origins =
                new CorsProperties(" https://example.com , https://app.example.com ")
                        .allowedOriginList();

        assertThat(origins).containsExactly("https://example.com", "https://app.example.com");
    }

    @Test
    void allowedOriginList_blankEntryAmongValid_isFilteredOut() {
        List<String> origins = new CorsProperties("https://example.com,,  ").allowedOriginList();

        assertThat(origins).containsExactly("https://example.com");
    }
}
