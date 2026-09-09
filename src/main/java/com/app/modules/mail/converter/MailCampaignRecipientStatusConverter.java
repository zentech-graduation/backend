package com.app.modules.mail.converter;

import java.util.Locale;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.app.modules.mail.enums.MailCampaignRecipientStatus;

/**
 * Bridges the uppercase Java enum to the lowercase PostgreSQL {@code
 * mail_campaign_recipient_status} enum.
 */
@Converter(autoApply = false)
public class MailCampaignRecipientStatusConverter
        implements AttributeConverter<MailCampaignRecipientStatus, String> {

    @Override
    public String convertToDatabaseColumn(MailCampaignRecipientStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public MailCampaignRecipientStatus convertToEntityAttribute(String dbData) {
        return dbData == null
                ? null
                : MailCampaignRecipientStatus.valueOf(dbData.toUpperCase(Locale.ROOT));
    }
}
