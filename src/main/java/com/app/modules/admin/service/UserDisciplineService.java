package com.app.modules.admin.service;

import java.util.UUID;

import com.app.common.response.CursorPageResponse;
import com.app.modules.admin.dto.request.AdminActionRequest;
import com.app.modules.admin.dto.request.AdminWarnUserRequest;
import com.app.modules.admin.dto.response.AdminActionResponse;
import com.app.modules.admin.dto.response.AdminViolationResponse;
import com.app.modules.admin.dto.response.AdminWarnUserResponse;
import com.app.modules.admin.dto.response.UserWarningResponse;

/** Domain API for the warning and strike ladder. */
public interface UserDisciplineService {

    /**
     * Issues one warning against an account and applies a strike when it is the third.
     *
     * <p>Only an account whose role is {@code user} may be warned, and no actor may warn itself.
     * Warning a moderator or an administrator is refused: three warnings produce a strike, a strike
     * changes {@code users.status}, and a moderator able to reach an administrator's status that
     * way would reopen the escalation path the account-status endpoints were closed to prevent.
     *
     * <p>A warning counts toward the next strike while it is unrevoked, was issued after the
     * account's most recent unrevoked strike, and is inside the retention window. The three
     * conditions compose: a strike resets the count, and the window expires what the reset did not.
     *
     * <p>When the count reaches three, a strike is written and its consequence applied: strike one
     * suspends for seven days, strike two for thirty, strike three and above ban permanently. The
     * consequence is applied only when it is strictly stronger than the account's current state, so
     * a warning can never downgrade a penalty already in force. The strike row is written either
     * way, and the resulting status is reported so the caller can see which happened.
     *
     * <p>Everything above happens in one transaction: the audit rows, the warning, the strike, the
     * status change, and the notification enqueued through the outbox.
     *
     * @param actorId moderator or administrator issuing the warning
     * @param userId account being warned
     * @param request reason key and note
     * @return the warning, the new count, and the account's resulting status
     * @throws com.app.common.exception.AppException with {@code USER_NOT_FOUND} when no live
     *     account holds that id, {@code ADMIN_SELF_ACTION_NOT_ALLOWED} when the actor is the
     *     target, {@code ADMIN_TARGET_NOT_WARNABLE} when the target is not an ordinary account, or
     *     {@code WARNING_REASON_DISABLED} when the reason key is unknown or switched off
     */
    AdminWarnUserResponse issueWarning(UUID actorId, UUID userId, AdminWarnUserRequest request);

    /**
     * How many warnings currently count toward the account's next strike.
     *
     * <p>The single reader of the active-warning predicate outside the warning path itself. A
     * caller that recomputed the rule would produce a plausible number that drifts silently from
     * the one the strike decision uses, so the count is served from here rather than restated.
     *
     * @param userId account being counted
     * @return the count, zero for an account with no warnings that still count
     */
    long countActiveWarnings(UUID userId);

    /**
     * Cursor page of an account's violation history, newest first.
     *
     * <p>A moderator sees warnings. An administrator sees warnings and strikes interleaved. The
     * cursor is scoped per role, so a moderator replaying an administrator's cursor is rejected
     * rather than paged into rows its own listing would never have produced.
     *
     * @param actorId the caller, whose role is read from the source of truth and decides what the
     *     listing contains
     * @param userId account whose history to read
     * @param cursor opaque cursor from the previous page; null for the first page
     * @param limit requested page size, normalized to 1 to 100 with a default of 20
     * @param includeRevoked when true the page also carries revoked entries, each marked by a
     *     non-null {@code revokedAt}; when false it carries only entries that still stand, which is
     *     what a caller that does not ask gets. The cursor is scoped on this too, so a cursor from
     *     one listing is rejected by the other.
     * @return the page
     */
    CursorPageResponse<AdminViolationResponse> listViolations(
            UUID actorId, UUID userId, String cursor, int limit, boolean includeRevoked);

    /**
     * Revokes one warning.
     *
     * <p>Revokes that warning and nothing else. It does not revoke a strike the warning contributed
     * to, and it does not lift a suspension or a ban. Reversing a strike is a separate, explicit
     * decision: one click on a warning must never silently change an account's status.
     *
     * @param actorId administrator revoking the warning
     * @param warningId warning to revoke
     * @param request reason for the reversal
     * @return the audit row written
     * @throws com.app.common.exception.AppException with {@code WARNING_NOT_FOUND} when no row
     *     holds that id, or {@code ADMIN_INVALID_TRANSITION} when it is already revoked
     */
    AdminActionResponse revokeWarning(UUID actorId, UUID warningId, AdminActionRequest request);

    /**
     * Revokes one strike.
     *
     * <p>Revokes that strike and nothing else. The account's status is left exactly as it is;
     * lifting a suspension or a ban is a separate decision, taken through the account-status
     * endpoints. Revoking frees the strike's number, so the account can take that number again.
     *
     * @param actorId administrator revoking the strike
     * @param strikeId strike to revoke
     * @param request reason for the reversal
     * @return the audit row written
     * @throws com.app.common.exception.AppException with {@code STRIKE_NOT_FOUND} when no row holds
     *     that id, or {@code ADMIN_INVALID_TRANSITION} when it is already revoked
     */
    AdminActionResponse revokeStrike(UUID actorId, UUID strikeId, AdminActionRequest request);

    /**
     * Cursor page of the calling account's own unrevoked warnings, newest first.
     *
     * <p>Warnings only, never strikes, and never another account's. A warning that fires a
     * notification the account cannot then review is not actionable, which is the only reason this
     * read exists.
     *
     * @param userId the authenticated caller, which is also the only account it can read
     * @param cursor opaque cursor from the previous page; null for the first page
     * @param limit requested page size, normalized to 1 to 100 with a default of 20
     * @return the page
     */
    CursorPageResponse<UserWarningResponse> listOwnWarnings(UUID userId, String cursor, int limit);
}
