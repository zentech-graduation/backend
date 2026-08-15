package com.app.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import com.app.modules.users.controller.UserController;

/**
 * Confirms constants for paths no controller serves have been removed, so every remaining constant
 * in {@link ApiConstants} corresponds to a route the application actually handles.
 */
class ApiConstantsUnroutedFieldsTest {

    @Test
    void unroutedConstants_noLongerExist() {
        // SEARCH was removed here as unrouted and deliberately reinstated by P5, which added
        // GET /api/v1/users/search. The other two remain unrouted.
        assertThat(fieldNames(ApiConstants.Users.class)).doesNotContain("SUGGESTIONS", "ME_AVATAR");
        assertThat(fieldNames(ApiConstants.Posts.class)).doesNotContain("EXPLORE", "MEDIA");
        assertThat(fieldNames(ApiConstants.Hashtags.class)).doesNotContain("BY_NAME", "POSTS");
        assertThat(fieldNames(ApiConstants.Media.class)).doesNotContain("BY_ID");
        assertThat(fieldNames(ApiConstants.Messages.class))
                .doesNotContain("CONVERSATION_MESSAGES", "MESSAGE_BY_ID", "READ", "UNREAD_COUNT");
        assertThat(fieldNames(ApiConstants.Auth.class))
                .doesNotContain("OAUTH2_CALLBACK", "CHANGE_PASSWORD");
    }

    @Test
    void reinstatedSearchConstant_isRoutedByAController() {
        // The guard's contract is "no constant without a route", so a reinstated name must be
        // pinned to an actual handler rather than simply dropped from the exclusion list.
        assertThat(fieldNames(ApiConstants.Users.class)).contains("SEARCH");
        assertThat(ApiConstants.Users.SEARCH).isEqualTo("/search");
        assertThat(routedPaths(UserController.class))
                .contains(ApiConstants.Users.SEARCH, ApiConstants.Users.BY_USERNAME);
    }

    private static List<String> routedPaths(Class<?> controller) {
        return Arrays.stream(controller.getDeclaredMethods())
                .map(m -> m.getAnnotation(GetMapping.class))
                .filter(a -> a != null)
                .flatMap(a -> Arrays.stream(a.value()))
                .toList();
    }

    private static List<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).toList();
    }
}
