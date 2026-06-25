package com.app.modules.auth.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.app.modules.auth.enums.OAuthProvider;

class OAuthProviderConverterTest {

    private OAuthProviderConverter converter;

    @BeforeEach
    void setUp() {
        converter = new OAuthProviderConverter();
    }

    @Test
    void convertToDatabaseColumn_null_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToDatabaseColumn_validProvider_returnsLowercase() {
        assertThat(converter.convertToDatabaseColumn(OAuthProvider.GOOGLE)).isEqualTo("google");
        assertThat(converter.convertToDatabaseColumn(OAuthProvider.FACEBOOK)).isEqualTo("facebook");
        assertThat(converter.convertToDatabaseColumn(OAuthProvider.APPLE)).isEqualTo("apple");
    }

    @Test
    void convertToEntityAttribute_null_returnsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_lowercaseString_returnsEnum() {
        assertThat(converter.convertToEntityAttribute("google")).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(converter.convertToEntityAttribute("facebook"))
                .isEqualTo(OAuthProvider.FACEBOOK);
    }
}
