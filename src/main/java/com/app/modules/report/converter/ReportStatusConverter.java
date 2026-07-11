package com.app.modules.report.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.app.modules.report.enums.ReportStatus;

@Converter
public class ReportStatusConverter implements AttributeConverter<ReportStatus, String> {
    public String convertToDatabaseColumn(ReportStatus value) {
        return value == null ? null : value.name().toLowerCase();
    }

    public ReportStatus convertToEntityAttribute(String value) {
        return value == null ? null : ReportStatus.valueOf(value.toUpperCase());
    }
}
