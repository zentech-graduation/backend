package com.app.modules.story.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.app.modules.story.enums.StoryType;

/** Bridges the uppercase Java StoryType enum to the lowercase Postgres story_type enum. */
@Converter(autoApply = false)
public class StoryTypeConverter implements AttributeConverter<StoryType, String> {

    @Override
    public String convertToDatabaseColumn(StoryType attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public StoryType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : StoryType.valueOf(dbData.toUpperCase());
    }
}
