package com.app.modules.auth.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.auth.entity.User;
import com.app.modules.auth.entity.UserCredential;
import com.app.modules.auth.enums.UserRole;
import com.app.modules.auth.enums.UserStatus;

class UserStateValidatorTest {

    private UserStateValidator validator;

    @BeforeEach
    void setUp() {
        validator = new UserStateValidator();
    }

    // --- enforceActive ---

    @Test
    void enforceActive_bannedUser_throwsAccountLocked() {
        User user = userWithStatus(UserStatus.BANNED);

        assertThatThrownBy(() -> validator.enforceActive(user))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_ACCOUNT_LOCKED);
    }

    @Test
    void enforceActive_suspendedUser_throwsAccountInactive() {
        User user = userWithStatus(UserStatus.SUSPENDED);

        assertThatThrownBy(() -> validator.enforceActive(user))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_ACCOUNT_INACTIVE);
    }

    @Test
    void enforceActive_deactivatedUser_throwsAccountInactive() {
        User user = userWithStatus(UserStatus.DEACTIVATED);

        assertThatThrownBy(() -> validator.enforceActive(user))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_ACCOUNT_INACTIVE);
    }

    @Test
    void enforceActive_activeUser_doesNotThrow() {
        User user = userWithStatus(UserStatus.ACTIVE);

        assertThatCode(() -> validator.enforceActive(user)).doesNotThrowAnyException();
    }

    // --- enforceEmailVerified ---

    @Test
    void enforceEmailVerified_unverifiedCredential_throwsEmailNotVerified() {
        UserCredential cred = credentialVerified(false);

        assertThatThrownBy(() -> validator.enforceEmailVerified(cred))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_EMAIL_NOT_VERIFIED);
    }

    @Test
    void enforceEmailVerified_verifiedCredential_doesNotThrow() {
        UserCredential cred = credentialVerified(true);

        assertThatCode(() -> validator.enforceEmailVerified(cred)).doesNotThrowAnyException();
    }

    private static User userWithStatus(UserStatus status) {
        return User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("test@example.com")
                .role(UserRole.USER)
                .status(status)
                .isPrivate(false)
                .isVerified(false)
                .build();
    }

    private static UserCredential credentialVerified(boolean emailVerified) {
        return UserCredential.builder()
                .userId(UUID.randomUUID())
                .emailVerified(emailVerified)
                .build();
    }
}
