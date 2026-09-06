package com.app.modules.recommendation.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.app.modules.recommendation.enums.UserEventType;

/** Converts behavioural event types between Java constants and lowercase PostgreSQL enum values. */
@Converter(autoApply = false)
public class UserEventTypeConverter implements AttributeConverter<UserEventType, String> {

    @Override
    public String convertToDatabaseColumn(UserEventType attribute) {
        return attribute == null ? null : attribute.toJson();
    }

    @Override
    public UserEventType convertToEntityAttribute(String dbData) {
        return UserEventType.fromJson(dbData);
    }
}
