package com.app.modules.users.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.app.modules.users.enums.UserRole;

/**
 * Converts a lowercase {@code role} query parameter to the domain enum.
 *
 * <p>Spring's built-in string-to-enum converter calls {@code Enum.valueOf}, which would only accept
 * the uppercase constant name. Every role value on the wire is lowercase, matching {@link
 * UserRole#toJson()}.
 */
@Component
public class StringToUserRoleConverter implements Converter<String, UserRole> {

    @Override
    public UserRole convert(String source) {
        return UserRole.fromJson(source);
    }
}
