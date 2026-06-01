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
     * Returns the public-facing profile of the specified user.
     *
     * <p>Private accounts ({@code isPrivate = true}) return HTTP 401 regardless of authentication
     * state. For public accounts, counter fields ({@code followerCount}, {@code followingCount},
     * {@code postCount}) are populated only when {@code isAuthenticated} is {@code true}.
     *
     * @param targetUserId the profile owner's identifier
     * @param isAuthenticated whether the caller supplied a valid Bearer token
     * @return public profile DTO
     * @throws com.app.common.exception.AppException with {@code NOT_FOUND} if the target user does
     *     not exist or has been soft-deleted
     * @throws com.app.common.exception.AppException with {@code UNAUTHORIZED} if the target account
     *     is private
     */
    PublicUserProfileResponse getUserProfile(UUID targetUserId, boolean isAuthenticated);

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
