package com.app.modules.users.service.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.social.service.SocialService;
import com.app.modules.users.dto.request.UpdateProfileRequest;
import com.app.modules.users.dto.request.UpdateSettingsRequest;
import com.app.modules.users.dto.response.PublicUserProfileResponse;
import com.app.modules.users.dto.response.UserProfileResponse;
import com.app.modules.users.dto.response.UserSettingsResponse;
import com.app.modules.users.entity.User;
import com.app.modules.users.entity.UserSettings;
import com.app.modules.users.mapper.UserMapper;
import com.app.modules.users.repository.UserRepository;
import com.app.modules.users.repository.UserSettingsRepository;
import com.app.modules.users.service.UserService;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserSettingsRepository settingsRepository;
    private final UserMapper userMapper;
    private final SocialService socialService;

    public UserServiceImpl(
            UserRepository userRepository,
            UserSettingsRepository settingsRepository,
            UserMapper userMapper,
            SocialService socialService) {
        this.userRepository = userRepository;
        this.settingsRepository = settingsRepository;
        this.userMapper = userMapper;
        this.socialService = socialService;
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(UUID userId) {
        User user =
                userRepository
                        .findByIdAndDeletedAtIsNull(userId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.NOT_FOUND));
        return userMapper.toProfileResponse(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateMyProfile(UUID userId, UpdateProfileRequest request) {
        User user =
                userRepository
                        .findByIdAndDeletedAtIsNull(userId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.NOT_FOUND));

        if (request.username() != null) {
            String newUsername = request.username();
            if (!newUsername.equals(user.getUsername())
                    && userRepository.existsByUsernameAndDeletedAtIsNull(newUsername)) {
                throw new AppException(ApiErrorCode.USER_USERNAME_ALREADY_EXISTS);
            }
            user.setUsername(newUsername);
        }

        if (request.displayName() != null) {
            user.setDisplayName(
                    StringUtils.hasText(request.displayName()) ? request.displayName() : null);
        }

        if (request.bio() != null) {
            user.setBio(StringUtils.hasText(request.bio()) ? request.bio() : null);
        }

        if (request.avatarUrl() != null) {
            user.setAvatarUrl(
                    StringUtils.hasText(request.avatarUrl()) ? request.avatarUrl() : null);
        }

        if (request.websiteUrl() != null) {
            user.setWebsiteUrl(
                    StringUtils.hasText(request.websiteUrl()) ? request.websiteUrl() : null);
        }

        if (request.isPrivate() != null) {
            user.setPrivate(request.isPrivate());
        }

        userRepository.save(user);
        return userMapper.toProfileResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicUserProfileResponse getUserProfile(UUID viewerId, UUID targetUserId) {
        User user =
                userRepository
                        .findByIdAndDeletedAtIsNull(targetUserId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.NOT_FOUND));

        boolean isOwner = viewerId != null && viewerId.equals(targetUserId);

        // A block in either direction hides the account entirely; return NOT_FOUND so a blocked
        // caller cannot even confirm the account exists.
        if (!isOwner
                && viewerId != null
                && socialService.isBlockedBetween(viewerId, targetUserId)) {
            throw new AppException(ApiErrorCode.NOT_FOUND);
        }

        // Social counts are relationship-gated: the owner always sees them, a private account
        // reveals them only to accepted followers, and a public account reveals them to any
        // authenticated caller. Everyone else receives the profile card with counts masked to null.
        boolean detailed;
        if (isOwner) {
            detailed = true;
        } else if (user.isPrivate()) {
            detailed = viewerId != null && socialService.hasAcceptedFollow(viewerId, targetUserId);
        } else {
            detailed = viewerId != null;
        }

        Integer followerCount = detailed ? user.getFollowerCount() : null;
        Integer followingCount = detailed ? user.getFollowingCount() : null;
        Integer postCount = detailed ? user.getPostCount() : null;

        return userMapper.toPublicProfileResponse(user, followerCount, followingCount, postCount);
    }

    @Override
    @Transactional(readOnly = true)
    public UserSettingsResponse getMySettings(UUID userId) {
        UserSettings settings =
                settingsRepository
                        .findById(userId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.NOT_FOUND));
        return userMapper.toSettingsResponse(settings);
    }

    @Override
    @Transactional
    public UserSettingsResponse updateMySettings(UUID userId, UpdateSettingsRequest request) {
        UserSettings settings =
                settingsRepository
                        .findById(userId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.NOT_FOUND));

        if (request.notifyLikes() != null) {
            settings.setNotifyLikes(request.notifyLikes());
        }
        if (request.notifyComments() != null) {
            settings.setNotifyComments(request.notifyComments());
        }
        if (request.notifyFollows() != null) {
            settings.setNotifyFollows(request.notifyFollows());
        }
        if (request.notifyMentions() != null) {
            settings.setNotifyMentions(request.notifyMentions());
        }
        if (request.notifyMessages() != null) {
            settings.setNotifyMessages(request.notifyMessages());
        }
        if (request.showActivityStatus() != null) {
            settings.setShowActivityStatus(request.showActivityStatus());
        }
        if (request.allowStoryReplies() != null) {
            settings.setAllowStoryReplies(request.allowStoryReplies());
        }
        if (request.allowMessageRequests() != null) {
            settings.setAllowMessageRequests(request.allowMessageRequests());
        }

        settingsRepository.save(settings);
        return userMapper.toSettingsResponse(settings);
    }
}
