package com.app.modules.post.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.app.modules.post.enums.PostStatus;

/** Bridges the uppercase Java PostStatus enum to the lowercase Postgres post_status enum. */
@Converter(autoApply = false)
public class PostStatusConverter implements AttributeConverter<PostStatus, String> {

    @Override
    public String convertToDatabaseColumn(PostStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public PostStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : PostStatus.valueOf(dbData.toUpperCase());
    }
}
