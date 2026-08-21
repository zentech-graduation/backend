package com.app.modules.admin.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.app.modules.admin.enums.PlatformMetric;

/** Converts lowercase metric query parameters to domain enum values. */
@Component
public class PlatformMetricWebConverter implements Converter<String, PlatformMetric> {

    @Override
    public PlatformMetric convert(String source) {
        return PlatformMetric.fromJson(source);
    }
}
