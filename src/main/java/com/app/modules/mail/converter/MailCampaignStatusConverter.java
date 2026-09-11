package com.app.modules.mail.converter;

import java.util.Locale;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.app.modules.mail.enums.MailCampaignStatus;

/**
 * Bridges the uppercase Java enum to the lowercase PostgreSQL {@code mail_campaign_status} enum.
 */
@Converter(autoApply = false)
public class MailCampaignStatusConverter implements AttributeConverter<MailCampaignStatus, String> {

    @Override
    public String convertToDatabaseColumn(MailCampaignStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public MailCampaignStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : MailCampaignStatus.valueOf(dbData.toUpperCase(Locale.ROOT));
    }
}
