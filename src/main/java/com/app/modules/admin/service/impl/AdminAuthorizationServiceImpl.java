package com.app.modules.admin.service.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.admin.service.AdminAuthorizationService;
import com.app.modules.users.entity.User;
import com.app.modules.users.enums.UserRole;

@Service
public class AdminAuthorizationServiceImpl implements AdminAuthorizationService {

    @Override
    public void assertMayChangeUserStatus(UUID actorId, UserRole actorRole, User target) {
        if (actorRole != UserRole.ADMIN) {
            throw new AppException(ApiErrorCode.FORBIDDEN);
        }
        // Ordered before the protected-target rule so an administrator acting on itself is answered
        // with the more specific self-action code rather than the generic protected-target one.
        if (actorId.equals(target.getId())) {
            throw new AppException(ApiErrorCode.ADMIN_SELF_ACTION_NOT_ALLOWED);
        }
        if (target.getRole() == UserRole.ADMIN) {
            throw new AppException(ApiErrorCode.ADMIN_TARGET_PROTECTED);
        }
    }
}
