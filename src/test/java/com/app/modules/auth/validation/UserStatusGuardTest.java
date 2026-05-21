package com.app.modules.auth.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.auth.entity.User;
import com.app.modules.auth.enums.UserStatus;

@ExtendWith(MockitoExtension.class)
class UserStatusGuardTest {

    @Mock private User user;

    private UserStatusGuard guard;

    @BeforeEach
    void setUp() {
        guard = new UserStatusGuard();
    }

    @Test
    void requireActive_bannedUser_throwsAccountLocked() {
        when(user.getStatus()).thenReturn(UserStatus.BANNED);

        assertThatThrownBy(() -> guard.requireActive(user))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_ACCOUNT_LOCKED);
    }

    @Test
    void requireActive_suspendedUser_throwsAccountInactive() {
        when(user.getStatus()).thenReturn(UserStatus.SUSPENDED);

        assertThatThrownBy(() -> guard.requireActive(user))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_ACCOUNT_INACTIVE);
    }

    @Test
    void requireActive_deactivatedUser_throwsAccountInactive() {
        when(user.getStatus()).thenReturn(UserStatus.DEACTIVATED);

        assertThatThrownBy(() -> guard.requireActive(user))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_ACCOUNT_INACTIVE);
    }

    @Test
    void requireActive_activeUser_doesNotThrow() {
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);

        assertThatCode(() -> guard.requireActive(user)).doesNotThrowAnyException();
    }

    @Test
    void isActive_activeUser_returnsTrue() {
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);

        assertThat(guard.isActive(user)).isTrue();
    }

    @Test
    void isActive_bannedUser_returnsFalse() {
        when(user.getStatus()).thenReturn(UserStatus.BANNED);

        assertThat(guard.isActive(user)).isFalse();
    }

    @Test
    void isActive_suspendedUser_returnsFalse() {
        when(user.getStatus()).thenReturn(UserStatus.SUSPENDED);

        assertThat(guard.isActive(user)).isFalse();
    }

    @Test
    void isActive_deactivatedUser_returnsFalse() {
        when(user.getStatus()).thenReturn(UserStatus.DEACTIVATED);

        assertThat(guard.isActive(user)).isFalse();
    }
}
