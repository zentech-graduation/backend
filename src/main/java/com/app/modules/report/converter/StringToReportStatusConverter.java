package com.app.modules.report.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.app.modules.report.enums.ReportStatus;

/** Converts case-insensitive HTTP parameters to report statuses. */
@Component
public class StringToReportStatusConverter implements Converter<String, ReportStatus> {

    @Override
    public ReportStatus convert(String source) {
        return ReportStatus.fromJson(source);
    }
}
