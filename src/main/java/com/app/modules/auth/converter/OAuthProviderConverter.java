package com.app.modules.auth.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.app.modules.auth.enums.OAuthProvider;

/**
 * Bridges the uppercase Java {@link OAuthProvider} enum to the lowercase Postgres {@code
 * oauth_provider} enum.
 */
@Converter(autoApply = false)
public class OAuthProviderConverter implements AttributeConverter<OAuthProvider, String> {

    @Override
    public String convertToDatabaseColumn(OAuthProvider attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public OAuthProvider convertToEntityAttribute(String dbData) {
        return dbData == null ? null : OAuthProvider.valueOf(dbData.toUpperCase());
    }
}
