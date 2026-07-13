package com.app.modules.admin.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.app.modules.admin.enums.AdminActionType;

/** Converts admin action types between Java constants and lowercase PostgreSQL enum values. */
@Converter(autoApply = false)
public class AdminActionTypeConverter implements AttributeConverter<AdminActionType, String> {

    @Override
    public String convertToDatabaseColumn(AdminActionType attribute) {
        return attribute == null ? null : attribute.toJson();
    }

    @Override
    public AdminActionType convertToEntityAttribute(String dbData) {
        return AdminActionType.fromJson(dbData);
    }
}
