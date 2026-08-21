package com.app.modules.users.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.app.modules.users.enums.UserStatus;

/**
 * Converts a lowercase {@code status} query parameter to the domain enum.
 *
 * <p>Spring's built-in string-to-enum converter calls {@code Enum.valueOf}, which would only accept
 * the uppercase constant name. Every status value on the wire is lowercase, matching {@link
 * UserStatus#toJson()}.
 */
@Component
public class StringToUserStatusConverter implements Converter<String, UserStatus> {

    @Override
    public UserStatus convert(String source) {
        return UserStatus.fromJson(source);
    }
}
