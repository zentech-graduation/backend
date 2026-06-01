package com.app.modules.social.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.app.modules.social.enums.FollowStatus;

/** Bridges the uppercase Java FollowStatus enum to the lowercase Postgres follow_status enum. */
@Converter(autoApply = false)
public class FollowStatusConverter implements AttributeConverter<FollowStatus, String> {

    @Override
    public String convertToDatabaseColumn(FollowStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public FollowStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : FollowStatus.valueOf(dbData.toUpperCase());
    }
}
