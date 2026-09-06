package com.app.modules.admin.service;

import java.util.UUID;

import com.app.modules.users.enums.UserStatus;

/**
 * Reinstatement of accounts whose fixed-term suspension has lapsed.
 *
 * <p>{@code users.status} stays authoritative for the authorization decision on any request. {@code
 * suspended_until} decides only when a suspended status ends, and is meaningless while the status
 * is anything else. When the two disagree, expiry wins and the reading path repairs the row.
 *
 * <p>Two callers drive this, and both run the same conditional update: the authentication path, so
 * a user whose suspension has just lapsed is admitted on the very first attempt rather than being
 * rejected once and admitted on retry, and a scheduled sweep, so an account nobody tries to log
 * into still returns to active and still leaves an audit trail.
 */
public interface SuspensionExpiryService {

    /**
     * Reinstates one account if its fixed-term suspension has lapsed, then reports the status the
     * row now holds.
     *
     * <p>A zero row count is success, not failure: it means a concurrent caller applied the same
     * repair first, or an administrator has since changed the status. Either way the returned value
     * is read back from the row afterwards, so it is authoritative for the caller's decision.
     *
     * <p>Writes one {@code unsuspend_user} audit row with a null actor when, and only when, this
     * call is the one that performed the reinstatement.
     *
     * @param userId account to consider
     * @return the account's status after the attempt, or null when no row holds that id
     */
    UserStatus reinstateIfExpired(UUID userId);

    /**
     * Reinstates up to {@code limit} accounts whose fixed-term suspension has lapsed.
     *
     * @param limit maximum accounts to reinstate in this pass
     * @return number of accounts this pass returned to active
     */
    int reinstateExpiredBatch(int limit);
}
