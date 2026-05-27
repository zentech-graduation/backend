package com.app.modules.users.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.app.modules.users.enums.UserStatus;

/**
 * Bridges the uppercase Java {@link UserStatus} enum to the lowercase Postgres {@code user_status}
 * enum.
 */
@Converter(autoApply = false)
public class UserStatusConverter implements AttributeConverter<UserStatus, String> {

    @Override
    public String convertToDatabaseColumn(UserStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public UserStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : UserStatus.valueOf(dbData.toUpperCase());
    }
}
