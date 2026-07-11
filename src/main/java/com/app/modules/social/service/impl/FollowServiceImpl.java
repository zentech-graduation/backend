package com.app.modules.social.service.impl;

import java.sql.SQLException;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.social.dto.response.FollowResponse;
import com.app.modules.social.entity.Follow;
import com.app.modules.social.entity.FollowId;
import com.app.modules.social.enums.FollowStatus;
import com.app.modules.social.mapper.FollowMapper;
import com.app.modules.social.repository.BlockRepository;
import com.app.modules.social.repository.FollowRepository;
import com.app.modules.social.service.FollowService;
import com.app.modules.social.service.SocialEventService;
import com.app.modules.users.entity.User;
import com.app.modules.users.repository.UserRepository;

@Service
public class FollowServiceImpl implements FollowService {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String CHECK_VIOLATION = "23514";

    private final FollowRepository followRepository;
    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final SocialEventService socialEventService;
    private final FollowMapper followMapper;

    public FollowServiceImpl(
            FollowRepository followRepository,
            BlockRepository blockRepository,
            UserRepository userRepository,
            SocialEventService socialEventService,
            FollowMapper followMapper) {
        this.followRepository = followRepository;
        this.blockRepository = blockRepository;
        this.userRepository = userRepository;
        this.socialEventService = socialEventService;
        this.followMapper = followMapper;
    }

    @Override
    @Transactional
    public FollowResponse follow(UUID followerId, UUID targetUserId) {
        validateDifferentUsers(followerId, targetUserId);
        User targetUser =
                userRepository
                        .findByIdAndDeletedAtIsNull(targetUserId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.NOT_FOUND));
        if (blockRepository.existsBetween(followerId, targetUserId)) {
            throw new AppException(ApiErrorCode.SOCIAL_FOLLOW_BLOCKED);
        }

        FollowId followId = new FollowId(followerId, targetUserId);
        if (followRepository.existsById(followId)) {
            throw new AppException(ApiErrorCode.SOCIAL_FOLLOW_ALREADY_EXISTS);
        }

        FollowStatus status = targetUser.isPrivate() ? FollowStatus.PENDING : FollowStatus.ACCEPTED;
        Follow follow = insertFollow(followerId, targetUserId, status);
        socialEventService.publishFollowCreated(follow);
        if (status == FollowStatus.ACCEPTED) {
            socialEventService.publishProfileFollowInteraction(followerId, targetUserId);
        }
        return followMapper.toResponse(follow);
    }

    private static void validateDifferentUsers(UUID followerId, UUID targetUserId) {
        if (followerId == null || targetUserId == null) {
            throw new AppException(ApiErrorCode.BAD_REQUEST);
        }
        if (followerId.equals(targetUserId)) {
            throw new AppException(ApiErrorCode.SOCIAL_SELF_FOLLOW_NOT_ALLOWED);
        }
    }

    private Follow insertFollow(UUID followerId, UUID targetUserId, FollowStatus status) {
        try {
            return followRepository.insert(followerId, targetUserId, status);
        } catch (DataIntegrityViolationException ex) {
            throw mapIntegrityViolation(ex);
        }
    }

    private static RuntimeException mapIntegrityViolation(DataIntegrityViolationException ex) {
        String sqlState = findSqlState(ex);
        if (UNIQUE_VIOLATION.equals(sqlState)) {
            return new AppException(ApiErrorCode.SOCIAL_FOLLOW_ALREADY_EXISTS);
        }
        if (CHECK_VIOLATION.equals(sqlState)) {
            return new AppException(ApiErrorCode.SOCIAL_SELF_FOLLOW_NOT_ALLOWED);
        }
        if (FOREIGN_KEY_VIOLATION.equals(sqlState)) {
            return new AppException(ApiErrorCode.NOT_FOUND);
        }
        return ex;
    }

    private static String findSqlState(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
            current = current.getCause();
        }
        return null;
    }
}
