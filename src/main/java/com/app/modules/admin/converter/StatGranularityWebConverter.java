package com.app.modules.admin.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.app.modules.admin.enums.StatGranularity;

/** Converts lowercase bucket-width query parameters to domain enum values. */
@Component
public class StatGranularityWebConverter implements Converter<String, StatGranularity> {

    @Override
    public StatGranularity convert(String source) {
        return StatGranularity.fromJson(source);
    }
}
