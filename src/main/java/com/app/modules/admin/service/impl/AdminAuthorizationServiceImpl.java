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
        switch (evaluateActorAndTarget(actorId, actorRole, target.getId(), target.getRole())) {
            case ALLOWED -> {}
            case ACTOR_NOT_ADMIN -> throw new AppException(ApiErrorCode.FORBIDDEN);
            case SELF_TARGET -> throw new AppException(ApiErrorCode.ADMIN_SELF_ACTION_NOT_ALLOWED);
            // A status change publishes a code naming the protected target directly, while a role
            // change folds the same condition into its transition code. Both are existing contracts
            // and neither moves just because the rule behind them is now evaluated once.
            default -> throw new AppException(ApiErrorCode.ADMIN_TARGET_PROTECTED);
        }
    }

    @Override
    public Outcome evaluateRoleTransition(
            UUID actorId,
            UserRole actorRole,
            UUID targetId,
            UserRole targetRole,
            UserRole requestedRole) {
        Outcome shared = evaluateActorAndTarget(actorId, actorRole, targetId, targetRole);
        if (shared != Outcome.ALLOWED) {
            return shared;
        }
        if (targetRole == requestedRole) {
            return Outcome.NO_OP;
        }
        if (targetRole == UserRole.USER && requestedRole == UserRole.ADMIN) {
            return Outcome.SKIP_LEVEL;
        }
        return Outcome.ALLOWED;
    }

    @Override
    public void assertMayChangeUserRole(
            UUID actorId,
            UserRole actorRole,
            UUID targetId,
            UserRole targetRole,
            UserRole requestedRole) {
        switch (evaluateRoleTransition(actorId, actorRole, targetId, targetRole, requestedRole)) {
            case ALLOWED -> {}
            case ACTOR_NOT_ADMIN -> throw new AppException(ApiErrorCode.FORBIDDEN);
            case SELF_TARGET -> throw new AppException(ApiErrorCode.ADMIN_SELF_ACTION_NOT_ALLOWED);
            case TARGET_IS_ADMIN, SKIP_LEVEL, NO_OP ->
                    throw new AppException(ApiErrorCode.ADMIN_ROLE_TRANSITION_NOT_ALLOWED);
        }
    }

    // The three rules every administrative action against an account shares, ordered so the most
    // specific applicable reason wins: an administrator acting on itself is answered with the
    // self-action reason rather than the generic protected-target one.
    private static Outcome evaluateActorAndTarget(
            UUID actorId, UserRole actorRole, UUID targetId, UserRole targetRole) {
        if (actorRole != UserRole.ADMIN) {
            return Outcome.ACTOR_NOT_ADMIN;
        }
        if (actorId.equals(targetId)) {
            return Outcome.SELF_TARGET;
        }
        if (targetRole == UserRole.ADMIN) {
            return Outcome.TARGET_IS_ADMIN;
        }
        return Outcome.ALLOWED;
    }
}
