package com.app.modules.users.service;

import java.util.UUID;

import com.app.modules.users.dto.request.UpdateProfileRequest;
import com.app.modules.users.dto.request.UpdateSettingsRequest;
import com.app.modules.users.dto.response.PublicUserProfileResponse;
import com.app.modules.users.dto.response.UserProfileResponse;
import com.app.modules.users.dto.response.UserSettingsResponse;

/**
 * Profile and settings management for user accounts. Authentication lifecycle (registration, login,
 * password reset) belongs to the auth module.
 */
public interface UserService {

    /**
     * Returns the full profile of the authenticated user.
     *
     * @param userId authenticated user's identifier
     * @return full profile including email
     * @throws com.app.common.exception.AppException with {@code NOT_FOUND} if the user does not
     *     exist or has been soft-deleted
     */
    UserProfileResponse getMyProfile(UUID userId);

    /**
     * Applies profile changes to the authenticated user's account.
     *
     * <p>Null fields in the request are ignored (no change). An empty string clears the field.
     * Username changes are checked for uniqueness against non-deleted users; a conflict throws a
     * 409.
     *
     * @param userId authenticated user's identifier
     * @param request partial update payload
     * @return updated profile
     * @throws com.app.common.exception.AppException with {@code USER_USERNAME_ALREADY_EXISTS} (HTTP
     *     409) if the requested username is already taken
     */
    UserProfileResponse updateMyProfile(UUID userId, UpdateProfileRequest request);

    /**
     * Returns the public-facing profile of the specified user, enforcing block and private-account
     * visibility rules.
     *
     * <p>A block in either direction between the viewer and the target yields {@code NOT_FOUND} so
     * a blocked caller cannot confirm the account exists. Social counter fields ({@code
     * followerCount}, {@code followingCount}, {@code postCount}) are relationship-gated: the owner
     * always sees them, a private account reveals them only to accepted followers, and a public
     * account reveals them to any authenticated caller. Any other viewer receives the profile card
     * with the counters masked to {@code null}.
     *
     * @param viewerId the authenticated caller's identifier, or {@code null} for an anonymous
     *     caller
     * @param targetUserId the profile owner's identifier
     * @return public profile DTO
     * @throws com.app.common.exception.AppException with {@code NOT_FOUND} if the target user does
     *     not exist, has been soft-deleted, or is blocked with respect to the viewer
     */
    PublicUserProfileResponse getUserProfile(UUID viewerId, UUID targetUserId);

    /**
     * Returns the notification and privacy settings for the authenticated user.
     *
     * @param userId authenticated user's identifier
     * @return settings DTO
     * @throws com.app.common.exception.AppException with {@code NOT_FOUND} if no settings row
     *     exists for the user
     */
    UserSettingsResponse getMySettings(UUID userId);

    /**
     * Applies settings changes for the authenticated user.
     *
     * <p>Null fields in the request are ignored (no change).
     *
     * @param userId authenticated user's identifier
     * @param request partial settings update payload
     * @return updated settings DTO
     * @throws com.app.common.exception.AppException with {@code NOT_FOUND} if no settings row
     *     exists for the user
     */
    UserSettingsResponse updateMySettings(UUID userId, UpdateSettingsRequest request);
}
