package com.app.modules.media.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.app.modules.media.enums.MediaType;

/** Bridges uppercase Java media types to lowercase PostgreSQL media_type enum values. */
@Converter(autoApply = false)
public class MediaTypeConverter implements AttributeConverter<MediaType, String> {

    @Override
    public String convertToDatabaseColumn(MediaType attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public MediaType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : MediaType.valueOf(dbData.toUpperCase());
    }
}
