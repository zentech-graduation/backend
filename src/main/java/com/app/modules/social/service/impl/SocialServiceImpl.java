package com.app.modules.social.service.impl;

import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.response.CursorPageResponse;
import com.app.modules.auth.entity.User;
import com.app.modules.auth.repository.UserRepository;
import com.app.modules.social.dto.response.FollowRequestResponse;
import com.app.modules.social.dto.response.FollowResponse;
import com.app.modules.social.dto.response.SocialUserSummaryResponse;
import com.app.modules.social.entity.Block;
import com.app.modules.social.entity.BlockId;
import com.app.modules.social.entity.Follow;
import com.app.modules.social.entity.FollowId;
import com.app.modules.social.enums.FollowStatus;
import com.app.modules.social.repository.BlockRepository;
import com.app.modules.social.repository.FollowRepository;
import com.app.modules.social.service.SocialService;

@Service
public class SocialServiceImpl implements SocialService {

    private final FollowRepository followRepository;
    private final BlockRepository blockRepository;
    private final UserRepository userRepository;

    public SocialServiceImpl(
            FollowRepository followRepository,
            BlockRepository blockRepository,
            UserRepository userRepository) {
        this.followRepository = followRepository;
        this.blockRepository = blockRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public FollowResponse followUser(UUID currentUserId, UUID targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new AppException(ApiErrorCode.SOCIAL_SELF_FOLLOW);
        }

        // Verify target user exists
        User targetUser =
                userRepository
                        .findByIdAndDeletedAtIsNull(targetUserId)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ApiErrorCode.NOT_FOUND, "Target user not found"));

        // Check block relationship
        if (blockRepository.existsById(new BlockId(currentUserId, targetUserId))
                || blockRepository.existsById(new BlockId(targetUserId, currentUserId))) {
            throw new AppException(ApiErrorCode.SOCIAL_BLOCKED);
        }

        FollowId followId = new FollowId(currentUserId, targetUserId);
        if (followRepository.existsById(followId)) {
            Follow existingFollow = followRepository.findById(followId).orElseThrow();
            if (existingFollow.getStatus() == FollowStatus.ACCEPTED) {
                throw new AppException(ApiErrorCode.SOCIAL_ALREADY_FOLLOWING);
            } else {
                throw new AppException(ApiErrorCode.SOCIAL_ALREADY_REQUESTED);
            }
        }

        FollowStatus status = targetUser.isPrivate() ? FollowStatus.PENDING : FollowStatus.ACCEPTED;
        Follow follow = Follow.builder().id(followId).status(status).build();

        followRepository.save(follow);

        return new FollowResponse(currentUserId, targetUserId, status);
    }

    @Override
    @Transactional
    public void unfollowUser(UUID currentUserId, UUID targetUserId) {
        // Verify target user exists
        if (!userRepository.existsById(targetUserId)) {
            throw new AppException(ApiErrorCode.NOT_FOUND, "Target user not found");
        }

        FollowId followId = new FollowId(currentUserId, targetUserId);
        Follow follow =
                followRepository
                        .findById(followId)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ApiErrorCode.NOT_FOUND,
                                                "Follow relationship not found"));

        followRepository.delete(follow);
    }

    @Override
    @Transactional
    public void respondToFollowRequest(UUID currentUserId, UUID requesterId, String action) {
        FollowId followId = new FollowId(requesterId, currentUserId);
        Follow follow =
                followRepository
                        .findByIdAndStatus(followId, FollowStatus.PENDING)
                        .orElseThrow(() -> new AppException(ApiErrorCode.SOCIAL_REQUEST_NOT_FOUND));

        if ("approve".equalsIgnoreCase(action)) {
            follow.setStatus(FollowStatus.ACCEPTED);
            followRepository.save(follow);
        } else if ("reject".equalsIgnoreCase(action)) {
            followRepository.delete(follow);
        } else {
            throw new AppException(
                    ApiErrorCode.BAD_REQUEST, "Invalid follow request response action");
        }
    }

    @Override
    @Transactional
    public void blockUser(UUID currentUserId, UUID targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new AppException(ApiErrorCode.SOCIAL_SELF_BLOCK);
        }

        // Verify target user exists
        if (!userRepository.existsById(targetUserId)) {
            throw new AppException(ApiErrorCode.NOT_FOUND, "Target user not found");
        }

        BlockId blockId = new BlockId(currentUserId, targetUserId);
        if (blockRepository.existsById(blockId)) {
            throw new AppException(ApiErrorCode.SOCIAL_ALREADY_BLOCKED);
        }

        Block block = Block.builder().id(blockId).build();
        blockRepository.save(block);

        // Clean up follow relationships both ways
        FollowId followIdDirect = new FollowId(currentUserId, targetUserId);
        followRepository.findById(followIdDirect).ifPresent(followRepository::delete);

        FollowId followIdReverse = new FollowId(targetUserId, currentUserId);
        followRepository.findById(followIdReverse).ifPresent(followRepository::delete);
    }

    @Override
    @Transactional
    public void unblockUser(UUID currentUserId, UUID targetUserId) {
        BlockId blockId = new BlockId(currentUserId, targetUserId);
        Block block =
                blockRepository
                        .findById(blockId)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ApiErrorCode.NOT_FOUND,
                                                "Block relationship not found"));

        blockRepository.delete(block);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<SocialUserSummaryResponse> getFollowers(
            UUID targetUserId, UUID currentUserId, String cursor, int limit) {

        // Verify target user exists
        userRepository
                .findByIdAndDeletedAtIsNull(targetUserId)
                .orElseThrow(() -> new AppException(ApiErrorCode.NOT_FOUND, "User not found"));

        // Check block relationship
        if (blockRepository.existsById(new BlockId(currentUserId, targetUserId))
                || blockRepository.existsById(new BlockId(targetUserId, currentUserId))) {
            throw new AppException(ApiErrorCode.SOCIAL_BLOCKED);
        }

        int size = limit > 100 ? 100 : (limit < 1 ? 20 : limit);
        OffsetDateTime cursorTime = decodeCursor(cursor);

        Pageable pageable = PageRequest.of(0, size);
        List<Follow> follows =
                followRepository.findFollowersWithCursor(
                        targetUserId, currentUserId, FollowStatus.ACCEPTED, cursorTime, pageable);

        if (follows.isEmpty()) {
            return CursorPageResponse.of(Collections.emptyList(), size, null, null, cursor != null);
        }

        List<UUID> followerIds = follows.stream().map(f -> f.getId().getFollowerId()).toList();

        Map<UUID, User> userMap =
                userRepository.findAllById(followerIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        List<SocialUserSummaryResponse> content =
                follows.stream()
                        .map(
                                f -> {
                                    User user = userMap.get(f.getId().getFollowerId());
                                    return new SocialUserSummaryResponse(
                                            user.getId(),
                                            user.getUsername(),
                                            user.getDisplayName(),
                                            user.getAvatarUrl(),
                                            user.isVerified());
                                })
                        .toList();

        String startCursor = encodeCursor(follows.get(0).getCreatedAt());
        String endCursor = encodeCursor(follows.get(follows.size() - 1).getCreatedAt());

        return CursorPageResponse.of(content, size, startCursor, endCursor, cursor != null);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<SocialUserSummaryResponse> getFollowing(
            UUID targetUserId, UUID currentUserId, String cursor, int limit) {

        // Verify target user exists
        userRepository
                .findByIdAndDeletedAtIsNull(targetUserId)
                .orElseThrow(() -> new AppException(ApiErrorCode.NOT_FOUND, "User not found"));

        // Check block relationship
        if (blockRepository.existsById(new BlockId(currentUserId, targetUserId))
                || blockRepository.existsById(new BlockId(targetUserId, currentUserId))) {
            throw new AppException(ApiErrorCode.SOCIAL_BLOCKED);
        }

        int size = limit > 100 ? 100 : (limit < 1 ? 20 : limit);
        OffsetDateTime cursorTime = decodeCursor(cursor);

        Pageable pageable = PageRequest.of(0, size);
        List<Follow> follows =
                followRepository.findFollowingWithCursor(
                        targetUserId, currentUserId, FollowStatus.ACCEPTED, cursorTime, pageable);

        if (follows.isEmpty()) {
            return CursorPageResponse.of(Collections.emptyList(), size, null, null, cursor != null);
        }

        List<UUID> followingIds = follows.stream().map(f -> f.getId().getFollowingId()).toList();

        Map<UUID, User> userMap =
                userRepository.findAllById(followingIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        List<SocialUserSummaryResponse> content =
                follows.stream()
                        .map(
                                f -> {
                                    User user = userMap.get(f.getId().getFollowingId());
                                    return new SocialUserSummaryResponse(
                                            user.getId(),
                                            user.getUsername(),
                                            user.getDisplayName(),
                                            user.getAvatarUrl(),
                                            user.isVerified());
                                })
                        .toList();

        String startCursor = encodeCursor(follows.get(0).getCreatedAt());
        String endCursor = encodeCursor(follows.get(follows.size() - 1).getCreatedAt());

        return CursorPageResponse.of(content, size, startCursor, endCursor, cursor != null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowRequestResponse> getPendingFollowRequests(UUID currentUserId) {
        List<Follow> pendingFollows =
                followRepository.findByIdFollowingIdAndStatusOrderByCreatedAtDesc(
                        currentUserId, FollowStatus.PENDING);

        if (pendingFollows.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> requesterIds =
                pendingFollows.stream().map(f -> f.getId().getFollowerId()).toList();

        Map<UUID, User> userMap =
                userRepository.findAllById(requesterIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        return pendingFollows.stream()
                .map(
                        f -> {
                            User user = userMap.get(f.getId().getFollowerId());
                            SocialUserSummaryResponse followerSummary =
                                    new SocialUserSummaryResponse(
                                            user.getId(),
                                            user.getUsername(),
                                            user.getDisplayName(),
                                            user.getAvatarUrl(),
                                            user.isVerified());
                            return new FollowRequestResponse(
                                    user.getId(), followerSummary, f.getStatus(), f.getCreatedAt());
                        })
                .toList();
    }

    private String encodeCursor(OffsetDateTime time) {
        if (time == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(time.toString().getBytes());
    }

    private OffsetDateTime decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(cursor));
            return OffsetDateTime.parse(decoded);
        } catch (Exception e) {
            throw new AppException(ApiErrorCode.BAD_REQUEST, "Invalid cursor format");
        }
    }
}
