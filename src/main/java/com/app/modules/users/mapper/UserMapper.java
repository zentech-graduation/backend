package com.app.modules.users.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.app.common.response.ViewerRelationshipResponse;
import com.app.modules.users.dto.response.PublicUserProfileResponse;
import com.app.modules.users.dto.response.UserProfileResponse;
import com.app.modules.users.dto.response.UserSettingsResponse;
import com.app.modules.users.entity.User;
import com.app.modules.users.entity.UserSettings;

/** Maps {@link User} and {@link UserSettings} entities to users-layer DTOs. */
@Mapper(componentModel = "spring")
public interface UserMapper {

    /**
     * Maps a {@link User} to the full profile response shown to the authenticated owner.
     *
     * @param user the user entity
     * @return full profile DTO including email
     */
    @Mapping(target = "isPrivate", source = "private")
    @Mapping(target = "isVerified", source = "verified")
    UserProfileResponse toProfileResponse(User user);

    /**
     * Maps a {@link User} to the public profile response shown to other callers.
     *
     * @param user the user entity
     * @param followerCount follower count to include, or {@code null} when unauthenticated
     * @param followingCount following count to include, or {@code null} when unauthenticated
     * @param postCount post count to include, or {@code null} when unauthenticated
     * @param viewerState the viewer's relationship to this user, batch-resolved by the service
     * @return public profile DTO excluding email and role
     */
    @Mapping(target = "isPrivate", source = "user.private")
    @Mapping(target = "isVerified", source = "user.verified")
    @Mapping(target = "followerCount", source = "followerCount")
    @Mapping(target = "followingCount", source = "followingCount")
    @Mapping(target = "postCount", source = "postCount")
    @Mapping(target = "viewerState", source = "viewerState")
    PublicUserProfileResponse toPublicProfileResponse(
            User user,
            Integer followerCount,
            Integer followingCount,
            Integer postCount,
            ViewerRelationshipResponse viewerState);

    /**
     * Maps a {@link UserSettings} to the settings response.
     *
     * @param settings the settings entity
     * @return settings DTO
     */
    UserSettingsResponse toSettingsResponse(UserSettings settings);
}
