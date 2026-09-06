package com.app.modules.hashtag.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.app.modules.hashtag.enums.HashtagStatus;

/** Bridges the uppercase Java enum to the lowercase PostgreSQL {@code hashtag_status} enum. */
@Converter(autoApply = false)
public class HashtagStatusConverter implements AttributeConverter<HashtagStatus, String> {

    @Override
    public String convertToDatabaseColumn(HashtagStatus attribute) {
        return attribute == null ? null : attribute.toJson();
    }

    @Override
    public HashtagStatus convertToEntityAttribute(String dbData) {
        return HashtagStatus.fromJson(dbData);
    }
}
