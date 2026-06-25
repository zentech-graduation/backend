package com.app.modules.notification.entity.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.app.modules.notification.entity.enums.NotificationType;

class NotificationTypeConverterTest {

    private NotificationTypeConverter converter;

    @BeforeEach
    void setUp() {
        converter = new NotificationTypeConverter();
    }

    @Test
    void convertToDatabaseColumn_null_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToDatabaseColumn_validType_returnsLowercase() {
        assertThat(converter.convertToDatabaseColumn(NotificationType.FOLLOW)).isEqualTo("follow");
        assertThat(converter.convertToDatabaseColumn(NotificationType.LIKE_POST))
                .isEqualTo("like_post");
        assertThat(converter.convertToDatabaseColumn(NotificationType.COMMENT_POST))
                .isEqualTo("comment_post");
    }

    @Test
    void convertToEntityAttribute_null_returnsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_lowercaseString_returnsEnum() {
        assertThat(converter.convertToEntityAttribute("follow")).isEqualTo(NotificationType.FOLLOW);
        assertThat(converter.convertToEntityAttribute("like_post"))
                .isEqualTo(NotificationType.LIKE_POST);
    }
}
