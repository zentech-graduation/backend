package com.app.modules.notification.entity.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.app.modules.notification.entity.enums.NotificationType;

@Converter(autoApply = false)
public class NotificationTypeConverter implements AttributeConverter<NotificationType, String> {

    @Override
    public String convertToDatabaseColumn(NotificationType attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name().toLowerCase();
    }

    @Override
    public NotificationType convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return NotificationType.valueOf(dbData.toUpperCase());
    }
}
