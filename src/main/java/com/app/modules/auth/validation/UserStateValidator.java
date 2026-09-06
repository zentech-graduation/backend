package com.app.modules.auth.validation;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.admin.service.SuspensionExpiryService;
import com.app.modules.auth.entity.UserCredential;
import com.app.modules.users.entity.User;
import com.app.modules.users.enums.UserStatus;

@Component
public class UserStateValidator {

    private final SuspensionExpiryService suspensionExpiryService;

    public UserStateValidator(SuspensionExpiryService suspensionExpiryService) {
        this.suspensionExpiryService = suspensionExpiryService;
    }

    /**
     * Rejects an account that may not authenticate, after first returning a lapsed fixed-term
     * suspension to active.
     *
     * <p>The repair runs before the status decision and the decision uses the status read back
     * afterwards. Deciding on the pre-repair status would reject a user whose suspension had just
     * lapsed exactly once and admit them on retry, which presents as intermittent flakiness and is
     * very hard to diagnose from either side.
     */
    public void enforceActive(User user) {
        UserStatus status = user.getStatus();
        if (status == UserStatus.SUSPENDED && hasLapsed(user.getSuspendedUntil())) {
            status = suspensionExpiryService.reinstateIfExpired(user.getId());
            if (status == UserStatus.ACTIVE) {
                // Keep the in-memory row consistent with what the repair committed. Applied only on
                // the ACTIVE outcome: writing ACTIVE unconditionally would let a stale entity flush
                // over a concurrent ban.
                user.setStatus(UserStatus.ACTIVE);
                user.setSuspendedUntil(null);
            }
        }
        if (status == null) {
            throw new AppException(ApiErrorCode.AUTH_ACCOUNT_INACTIVE);
        }
        switch (status) {
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

    // An indefinite suspension stores a null deadline and never lapses.
    private static boolean hasLapsed(OffsetDateTime suspendedUntil) {
        return suspendedUntil != null && !suspendedUntil.isAfter(OffsetDateTime.now());
    }
}
