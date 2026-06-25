package com.app.modules.social.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.app.modules.social.enums.FollowStatus;

class FollowStatusConverterTest {

    private FollowStatusConverter converter;

    @BeforeEach
    void setUp() {
        converter = new FollowStatusConverter();
    }

    @Test
    void convertToDatabaseColumn_null_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToDatabaseColumn_validStatus_returnsLowercase() {
        assertThat(converter.convertToDatabaseColumn(FollowStatus.PENDING)).isEqualTo("pending");
        assertThat(converter.convertToDatabaseColumn(FollowStatus.ACCEPTED)).isEqualTo("accepted");
    }

    @Test
    void convertToEntityAttribute_null_returnsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_lowercaseString_returnsEnum() {
        assertThat(converter.convertToEntityAttribute("pending")).isEqualTo(FollowStatus.PENDING);
        assertThat(converter.convertToEntityAttribute("accepted")).isEqualTo(FollowStatus.ACCEPTED);
    }
}
