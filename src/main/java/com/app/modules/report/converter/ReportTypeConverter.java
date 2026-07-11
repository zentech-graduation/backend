package com.app.modules.report.converter;

import java.util.Locale;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.app.modules.report.enums.ReportType;

/** Bridges the uppercase Java enum to the lowercase PostgreSQL {@code report_type} enum. */
@Converter(autoApply = false)
public class ReportTypeConverter implements AttributeConverter<ReportType, String> {

    @Override
    public String convertToDatabaseColumn(ReportType attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public ReportType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ReportType.valueOf(dbData.toUpperCase(Locale.ROOT));
    }
}
