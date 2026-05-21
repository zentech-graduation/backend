package com.app.modules.auth.validation;

import org.springframework.stereotype.Component;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.auth.entity.User;
import com.app.modules.auth.enums.UserStatus;

@Component
public class UserStatusGuard {

    public void requireActive(User user) {
        switch (user.getStatus()) {
            case BANNED -> throw new AppException(ApiErrorCode.AUTH_ACCOUNT_LOCKED);
            case SUSPENDED, DEACTIVATED ->
                    throw new AppException(ApiErrorCode.AUTH_ACCOUNT_INACTIVE);
            default -> {}
        }
    }

    public boolean isActive(User user) {
        return user.getStatus() == UserStatus.ACTIVE;
    }
}
