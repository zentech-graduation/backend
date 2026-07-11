package com.app.modules.report.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.app.modules.report.enums.ReportType;

@Converter
public class ReportTypeConverter implements AttributeConverter<ReportType, String> {
    public String convertToDatabaseColumn(ReportType value) {
        return value == null ? null : value.name().toLowerCase();
    }

    public ReportType convertToEntityAttribute(String value) {
        return value == null ? null : ReportType.valueOf(value.toUpperCase());
    }
}
