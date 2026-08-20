package com.app.modules.hashtag.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.app.modules.hashtag.enums.HashtagStatus;

/**
 * Converts a lowercase {@code status} query parameter to the domain enum.
 *
 * <p>Spring's built-in string-to-enum converter calls {@code Enum.valueOf}, which would only accept
 * the uppercase constant name. Every status value on the wire is lowercase, matching {@link
 * HashtagStatus#toJson()}.
 */
@Component
public class StringToHashtagStatusConverter implements Converter<String, HashtagStatus> {

    @Override
    public HashtagStatus convert(String source) {
        return HashtagStatus.fromJson(source);
    }
}
