package com.app.modules.auth.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.app.modules.auth.enums.UserRole;

/**
 * Bridges the uppercase Java {@link UserRole} enum to the lowercase Postgres {@code user_role}
 * enum. Required because Java enum convention is uppercase while the database enum was defined
 * lowercase by the V01 migration.
 */
@Converter(autoApply = false)
public class UserRoleConverter implements AttributeConverter<UserRole, String> {

    @Override
    public String convertToDatabaseColumn(UserRole attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public UserRole convertToEntityAttribute(String dbData) {
        return dbData == null ? null : UserRole.valueOf(dbData.toUpperCase());
    }
}
