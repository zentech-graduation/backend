package com.app.modules.report.converter;

import java.util.Locale;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.app.modules.report.enums.ReportReason;

/** Bridges the uppercase Java enum to the lowercase PostgreSQL {@code report_reason} enum. */
@Converter(autoApply = false)
public class ReportReasonConverter implements AttributeConverter<ReportReason, String> {

    @Override
    public String convertToDatabaseColumn(ReportReason attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public ReportReason convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ReportReason.valueOf(dbData.toUpperCase(Locale.ROOT));
    }
}
