package com.app.common.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;

/** Static accessors for the currently-authenticated principal. */
public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Returns the {@link UserPrincipal} bound to the current security context.
     *
     * @return current principal
     * @throws AppException with {@link ApiErrorCode#UNAUTHORIZED} when no user is authenticated
     */
    public static UserPrincipal getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AppException(ApiErrorCode.UNAUTHORIZED);
        }
        return principal;
    }

    public static UUID getCurrentUserId() {
        return getCurrentUser().userId();
    }

    public static String getCurrentUserRole() {
        return getCurrentUser().role();
    }

    public static boolean isAdmin() {
        return "ADMIN".equals(getCurrentUserRole());
    }

    public static boolean isModerator() {
        String role = getCurrentUserRole();
        return "MODERATOR".equals(role) || "ADMIN".equals(role);
    }
}
