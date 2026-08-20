package com.app.modules.recommendation.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.app.modules.recommendation.enums.UserEventType;

/** Converts lowercase behavioural event query parameters to domain enum values. */
@Component
public class UserEventTypeWebConverter implements Converter<String, UserEventType> {

    @Override
    public UserEventType convert(String source) {
        return UserEventType.fromJson(source);
    }
}
