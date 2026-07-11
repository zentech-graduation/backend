package com.app.modules.report.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.app.modules.report.enums.ReportReason;

@Converter
public class ReportReasonConverter implements AttributeConverter<ReportReason, String> {
    public String convertToDatabaseColumn(ReportReason value) {
        return value == null ? null : value.name().toLowerCase();
    }

    public ReportReason convertToEntityAttribute(String value) {
        return value == null ? null : ReportReason.valueOf(value.toUpperCase());
    }
}
