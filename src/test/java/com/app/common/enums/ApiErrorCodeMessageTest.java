package com.app.common.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * Guards the user-visible default messages against describing a condition the code is not raised
 * for.
 *
 * <p>{@code AUTH_ACCOUNT_LOCKED} previously read "temporarily locked due to too many failed
 * attempts" while being raised only for a permanent ban, telling banned users to wait and retry for
 * a lockout that does not exist anywhere in the codebase.
 */
class ApiErrorCodeMessageTest {

    @Test
    void accountStateMessagesNameTheStateThatProducedThem() {
        assertThat(ApiErrorCode.AUTH_ACCOUNT_LOCKED.getDefaultMessage().toLowerCase(Locale.ROOT))
                .contains("banned")
                .doesNotContain("temporarily")
                .doesNotContain("failed attempts");
        assertThat(ApiErrorCode.AUTH_ACCOUNT_INACTIVE.getDefaultMessage().toLowerCase(Locale.ROOT))
                .contains("suspended")
                .contains("deactivated");
        assertThat(
                        ApiErrorCode.AUTH_EMAIL_NOT_VERIFIED
                                .getDefaultMessage()
                                .toLowerCase(Locale.ROOT))
                .contains("email")
                .contains("not been verified");
    }

    @Test
    void rateLimitMessageDoesNotBlameServerLoad() {
        // Raised only when the caller exceeds their own quota, never for server saturation.
        assertThat(ApiErrorCode.TOO_MANY_REQUESTS.getDefaultMessage().toLowerCase(Locale.ROOT))
                .doesNotContain("system is busy");
    }

    @Test
    void everyCodeCarriesANonBlankMessageAndMatchingCodeString() {
        for (ApiErrorCode code : ApiErrorCode.values()) {
            assertThat(code.getDefaultMessage()).as("%s message", code.name()).isNotBlank();
            assertThat(code.getCode()).as("%s code string", code.name()).isEqualTo(code.name());
        }
    }
}
