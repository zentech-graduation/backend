package com.app.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Confirms constants for paths no controller serves have been removed, so every remaining constant
 * in {@link ApiConstants} corresponds to a route the application actually handles.
 */
class ApiConstantsUnroutedFieldsTest {

    @Test
    void unroutedConstants_noLongerExist() {
        assertThat(fieldNames(ApiConstants.Users.class))
                .doesNotContain("SEARCH", "SUGGESTIONS", "ME_AVATAR");
        assertThat(fieldNames(ApiConstants.Posts.class)).doesNotContain("EXPLORE", "MEDIA");
        assertThat(fieldNames(ApiConstants.Hashtags.class)).doesNotContain("BY_NAME", "POSTS");
        assertThat(fieldNames(ApiConstants.Media.class)).doesNotContain("BY_ID");
        assertThat(fieldNames(ApiConstants.Messages.class))
                .doesNotContain("CONVERSATION_MESSAGES", "MESSAGE_BY_ID", "READ", "UNREAD_COUNT");
        assertThat(fieldNames(ApiConstants.Auth.class))
                .doesNotContain("OAUTH2_CALLBACK", "CHANGE_PASSWORD");
    }

    private static List<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).toList();
    }
}
