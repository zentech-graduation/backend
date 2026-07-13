package com.app.modules.report.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.app.modules.report.enums.ReportType;

/** Converts case-insensitive HTTP parameters to report target types. */
@Component
public class StringToReportTypeConverter implements Converter<String, ReportType> {

    @Override
    public ReportType convert(String source) {
        return ReportType.fromJson(source);
    }
}
