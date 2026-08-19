package com.app.modules.admin.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.users.enums.UserRole;

/**
 * The complete set of role transitions the API permits, in one place.
 *
 * <p>Permitted, and nothing else:
 *
 * <ul>
 *   <li>{@code user -> moderator}
 *   <li>{@code moderator -> user}
 *   <li>{@code moderator -> admin}
 * </ul>
 *
 * <p>Three rejections carry their own reason. An administrator is never a valid target, which makes
 * {@code admin -> moderator} unreachable on purpose: removing a rogue administrator is a
 * database-level operation, because an in-application lockout of the whole administrator tier has
 * no recovery path while a database escalation does. A skip-level {@code user -> admin} promotion
 * is refused so granting the highest privilege always passes through the moderator tier. A request
 * that names the role the account already holds is refused rather than silently succeeding, so a
 * successful response always means something changed.
 *
 * <p>Only an administrator may invoke a transition, and never against itself.
 */
@Component
public class RoleTransitionPolicy {

    /** Outcome of evaluating one requested transition. */
    public enum Outcome {
        ALLOWED,
        ACTOR_NOT_ADMIN,
        SELF_TARGET,
        TARGET_IS_ADMIN,
        SKIP_LEVEL,
        NO_OP
    }

    /**
     * Classifies a requested transition without throwing.
     *
     * <p>Checks are ordered so the most specific applicable reason is the one reported: actor
     * authority first, then self-targeting, then the protected target, then the transition itself.
     *
     * @param actorId the account performing the change
     * @param actorRole the actor's role as read from the source of truth, never from a token claim
     * @param targetId the account whose role would change
     * @param targetRole the target's current role
     * @param requestedRole the role the caller asked for
     * @return the outcome; {@link Outcome#ALLOWED} only for a transition in the permitted set
     */
    public Outcome evaluate(
            UUID actorId,
            UserRole actorRole,
            UUID targetId,
            UserRole targetRole,
            UserRole requestedRole) {
        if (actorRole != UserRole.ADMIN) {
            return Outcome.ACTOR_NOT_ADMIN;
        }
        if (actorId.equals(targetId)) {
            return Outcome.SELF_TARGET;
        }
        if (targetRole == UserRole.ADMIN) {
            return Outcome.TARGET_IS_ADMIN;
        }
        if (targetRole == requestedRole) {
            return Outcome.NO_OP;
        }
        if (targetRole == UserRole.USER && requestedRole == UserRole.ADMIN) {
            return Outcome.SKIP_LEVEL;
        }
        return Outcome.ALLOWED;
    }

    /**
     * Asserts that a requested transition is permitted.
     *
     * @param actorId the account performing the change
     * @param actorRole the actor's role as read from the source of truth
     * @param targetId the account whose role would change
     * @param targetRole the target's current role
     * @param requestedRole the role the caller asked for
     * @throws AppException {@code FORBIDDEN} when the actor is not an administrator, {@code
     *     ADMIN_SELF_ACTION_NOT_ALLOWED} when the actor targets itself, and {@code
     *     ADMIN_ROLE_TRANSITION_FORBIDDEN} for a protected target, a skip-level promotion, or a
     *     no-op
     */
    public void assertAllowed(
            UUID actorId,
            UserRole actorRole,
            UUID targetId,
            UserRole targetRole,
            UserRole requestedRole) {
        switch (evaluate(actorId, actorRole, targetId, targetRole, requestedRole)) {
            case ALLOWED -> {}
            case ACTOR_NOT_ADMIN -> throw new AppException(ApiErrorCode.FORBIDDEN);
            case SELF_TARGET -> throw new AppException(ApiErrorCode.ADMIN_SELF_ACTION_NOT_ALLOWED);
            case TARGET_IS_ADMIN, SKIP_LEVEL, NO_OP ->
                    throw new AppException(ApiErrorCode.ADMIN_ROLE_TRANSITION_FORBIDDEN);
        }
    }
}
