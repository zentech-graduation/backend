package com.app.modules.post.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.app.modules.post.enums.PostType;

/** Bridges the uppercase Java PostType enum to the lowercase Postgres post_type enum. */
@Converter(autoApply = false)
public class PostTypeConverter implements AttributeConverter<PostType, String> {

    @Override
    public String convertToDatabaseColumn(PostType attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public PostType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : PostType.valueOf(dbData.toUpperCase());
    }
}
