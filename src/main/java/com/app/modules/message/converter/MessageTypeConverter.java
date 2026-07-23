package com.app.modules.message.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.app.modules.message.enums.MessageType;

/** Bridges the uppercase Java MessageType enum to the lowercase Postgres message_type enum. */
@Converter(autoApply = false)
public class MessageTypeConverter implements AttributeConverter<MessageType, String> {

    @Override
    public String convertToDatabaseColumn(MessageType attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public MessageType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : MessageType.valueOf(dbData.toUpperCase());
    }
}
