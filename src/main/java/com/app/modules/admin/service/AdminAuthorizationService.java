package com.app.modules.admin.service;

import java.util.UUID;

import com.app.common.exception.AppException;
import com.app.modules.users.entity.User;
import com.app.modules.users.enums.UserRole;

/**
 * Every actor-and-target rule for an administrative action against an account, in one place.
 *
 * <p>The endpoint annotations and path matchers are the first gate; this is the second. A service
 * method reachable from a consumer, a scheduled job, or another service must not depend on a caller
 * having passed through the controller.
 *
 * <p>Status changes and role changes share the same first three rules: only an administrator may
 * act, nobody may act on their own account, and an administrator is never a valid target. They were
 * written separately and drifting apart was a matter of time, since a rule added to one place would
 * not have been visible from the other. They are evaluated once here and each caller maps the
 * outcome to the error code its own contract publishes.
 *
 * <p>An administrator being an invalid target makes removing a rogue administrator deliberately a
 * database-level operation. An in-application route to lock out the whole administrator tier has no
 * recovery path, because a banned account cannot authenticate to undo it; a database escalation
 * does have one.
 */
public interface AdminAuthorizationService {

    /** Outcome of evaluating one requested action, without throwing. */
    enum Outcome {
        ALLOWED,
        ACTOR_NOT_ADMIN,
        SELF_TARGET,
        TARGET_IS_ADMIN,
        SKIP_LEVEL,
        NO_OP
    }

    /**
     * Asserts that the actor may change the target account's status.
     *
     * @param actorId the account performing the action
     * @param actorRole the role resolved for {@code actorId} from the source of truth, never the
     *     role claim carried on a token
     * @param target the target account, already loaded inside the caller's transaction
     * @throws AppException {@code FORBIDDEN} when the actor is not an administrator, {@code
     *     ADMIN_SELF_ACTION_NOT_ALLOWED} when actor and target are the same account, and {@code
     *     ADMIN_TARGET_PROTECTED} when the target is an administrator
     */
    void assertMayChangeUserStatus(UUID actorId, UserRole actorRole, User target);

    /**
     * Classifies a requested role transition without throwing.
     *
     * <p>Permitted, and nothing else:
     *
     * <ul>
     *   <li>{@code user -> moderator}
     *   <li>{@code moderator -> user}
     *   <li>{@code moderator -> admin}
     * </ul>
     *
     * <p>Checks are ordered so the most specific applicable reason is the one reported: actor
     * authority first, then self-targeting, then the protected target, then the transition itself.
     * A skip-level {@code user -> admin} promotion is refused so granting the highest privilege
     * always passes through the moderator tier. A request naming the role the account already holds
     * is refused rather than silently succeeding, so a successful response always means something
     * changed.
     *
     * @param actorId the account performing the change
     * @param actorRole the actor's role as read from the source of truth, never from a token claim
     * @param targetId the account whose role would change
     * @param targetRole the target's current role
     * @param requestedRole the role the caller asked for
     * @return the outcome; {@link Outcome#ALLOWED} only for a transition in the permitted set
     */
    Outcome evaluateRoleTransition(
            UUID actorId,
            UserRole actorRole,
            UUID targetId,
            UserRole targetRole,
            UserRole requestedRole);

    /**
     * Asserts that a requested role transition is permitted.
     *
     * @param actorId the account performing the change
     * @param actorRole the actor's role as read from the source of truth
     * @param targetId the account whose role would change
     * @param targetRole the target's current role
     * @param requestedRole the role the caller asked for
     * @throws AppException {@code FORBIDDEN} when the actor is not an administrator, {@code
     *     ADMIN_SELF_ACTION_NOT_ALLOWED} when the actor targets itself, and {@code
     *     ADMIN_ROLE_TRANSITION_NOT_ALLOWED} for a protected target, a skip-level promotion, or a
     *     no-op
     */
    void assertMayChangeUserRole(
            UUID actorId,
            UserRole actorRole,
            UUID targetId,
            UserRole targetRole,
            UserRole requestedRole);
}
