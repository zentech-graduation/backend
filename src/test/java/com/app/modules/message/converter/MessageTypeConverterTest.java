package com.app.modules.message.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.app.modules.message.enums.MessageType;

class MessageTypeConverterTest {

    private MessageTypeConverter converter;

    @BeforeEach
    void setUp() {
        converter = new MessageTypeConverter();
    }

    @Test
    void convertToDatabaseColumn_null_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToDatabaseColumn_everyType_returnsLowercase() {
        assertThat(converter.convertToDatabaseColumn(MessageType.TEXT)).isEqualTo("text");
        assertThat(converter.convertToDatabaseColumn(MessageType.IMAGE)).isEqualTo("image");
        assertThat(converter.convertToDatabaseColumn(MessageType.VIDEO)).isEqualTo("video");
        assertThat(converter.convertToDatabaseColumn(MessageType.POST_SHARE))
                .isEqualTo("post_share");
        assertThat(converter.convertToDatabaseColumn(MessageType.STORY_SHARE))
                .isEqualTo("story_share");
    }

    @Test
    void convertToEntityAttribute_null_returnsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_everyLowercaseValue_returnsEnum() {
        assertThat(converter.convertToEntityAttribute("text")).isEqualTo(MessageType.TEXT);
        assertThat(converter.convertToEntityAttribute("image")).isEqualTo(MessageType.IMAGE);
        assertThat(converter.convertToEntityAttribute("video")).isEqualTo(MessageType.VIDEO);
        assertThat(converter.convertToEntityAttribute("post_share"))
                .isEqualTo(MessageType.POST_SHARE);
        assertThat(converter.convertToEntityAttribute("story_share"))
                .isEqualTo(MessageType.STORY_SHARE);
    }
}
