package com.app.modules.admin.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.app.modules.admin.enums.AdminActionType;

/** Converts lowercase admin action query parameters to domain enum values. */
@Component
public class AdminActionTypeWebConverter implements Converter<String, AdminActionType> {

    @Override
    public AdminActionType convert(String source) {
        return AdminActionType.fromJson(source);
    }
}
