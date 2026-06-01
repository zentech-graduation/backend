package com.app.modules.auth.validation;

import org.springframework.stereotype.Component;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.auth.entity.UserCredential;
import com.app.modules.users.entity.User;

@Component
public class UserStateValidator {

    public void enforceActive(User user) {
        switch (user.getStatus()) {
            case BANNED -> throw new AppException(ApiErrorCode.AUTH_ACCOUNT_LOCKED);
            case SUSPENDED, DEACTIVATED ->
                    throw new AppException(ApiErrorCode.AUTH_ACCOUNT_INACTIVE);
            default -> {}
        }
    }

    public void enforceEmailVerified(UserCredential credential) {
        if (!credential.isEmailVerified()) {
            throw new AppException(ApiErrorCode.AUTH_EMAIL_NOT_VERIFIED);
        }
    }
}
