package com.app.modules.admin.service;

import java.util.UUID;

import com.app.common.exception.AppException;
import com.app.modules.users.entity.User;
import com.app.modules.users.enums.UserRole;

/**
 * Authorization rules for moderation actions, enforced independently of the web layer.
 *
 * <p>The endpoint annotations and path matchers are the first gate; this is the second. A service
 * method reachable from a consumer, a scheduled job, or another service must not depend on a caller
 * having passed through the controller.
 */
public interface AdminAuthorizationService {

    /**
     * Asserts that the actor may change the target account's status.
     *
     * <p>Three rules, applied in order: only an administrator may change any account status; nobody
     * may act on their own account; and no caller may change an administrator's status through the
     * API at all. The last rule is absolute and has no actor that satisfies it, so removing a rogue
     * administrator is deliberately a database-level operation.
     *
     * @param actorId the account performing the action
     * @param actorRole the role resolved for {@code actorId} from the source of truth, never the
     *     role claim carried on a token
     * @param target the target account, already loaded inside the caller's transaction
     * @throws AppException when the actor is not an administrator, when actor and target are the
     *     same account, or when the target is an administrator
     */
    void assertMayChangeUserStatus(UUID actorId, UserRole actorRole, User target);
}
