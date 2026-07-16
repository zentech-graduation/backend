package com.app.modules.social.service.impl;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.response.CursorPageResponse;
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
import com.app.modules.social.repository.SocialUserRepository;
import com.app.modules.social.service.SocialEventService;
import com.app.modules.social.service.SocialService;
import com.app.modules.users.entity.User;

@Service
public class SocialServiceImpl implements SocialService {

    private static final String UNIQUE_VIOLATION_SQLSTATE = "23505";

    private final FollowRepository followRepository;
    private final BlockRepository blockRepository;
    private final SocialUserRepository socialUserRepository;
    private final SocialEventService socialEventService;

    public SocialServiceImpl(
            FollowRepository followRepository,
            BlockRepository blockRepository,
            SocialUserRepository socialUserRepository,
            SocialEventService socialEventService) {
        this.followRepository = followRepository;
        this.blockRepository = blockRepository;
        this.socialUserRepository = socialUserRepository;
        this.socialEventService = socialEventService;
    }

    @Override
    @Transactional
    public FollowResponse followUser(UUID currentUserId, UUID targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new AppException(ApiErrorCode.SOCIAL_SELF_FOLLOW);
        }

        User targetUser =
                socialUserRepository
                        .findByIdAndDeletedAtIsNull(targetUserId)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ApiErrorCode.NOT_FOUND, "Target user not found"));

        if (blockRepository.existsById(new BlockId(currentUserId, targetUserId))
                || blockRepository.existsById(new BlockId(targetUserId, currentUserId))) {
            throw new AppException(ApiErrorCode.SOCIAL_BLOCKED);
        }

        FollowId followId = new FollowId(currentUserId, targetUserId);

        if (followRepository.existsById(followId)) {
            Follow existingFollow = followRepository.findById(followId).orElseThrow();

            if (existingFollow.getStatus() == FollowStatus.ACCEPTED) {
                throw new AppException(ApiErrorCode.SOCIAL_ALREADY_FOLLOWING);
            }

            throw new AppException(ApiErrorCode.SOCIAL_ALREADY_REQUESTED);
        }

        FollowStatus status = targetUser.isPrivate() ? FollowStatus.PENDING : FollowStatus.ACCEPTED;

        Follow follow;
        try {
            // Single-statement INSERT ... RETURNING: the composite primary key rejects a
            // concurrent duplicate atomically, unlike save()/merge which silently no-ops on an
            // already-committed row and would re-publish the follow event.
            follow = followRepository.insert(currentUserId, targetUserId, status);
        } catch (DataIntegrityViolationException ex) {
            if (isUniqueViolation(ex)) {
                throw new AppException(
                        status == FollowStatus.PENDING
                                ? ApiErrorCode.SOCIAL_ALREADY_REQUESTED
                                : ApiErrorCode.SOCIAL_ALREADY_FOLLOWING);
            }
            throw ex;
        }

        socialEventService.publishFollowCreated(follow);

        return new FollowResponse(currentUserId, targetUserId, status, follow.getCreatedAt());
    }

    private static boolean isUniqueViolation(DataIntegrityViolationException ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return UNIQUE_VIOLATION_SQLSTATE.equals(sqlException.getSQLState());
            }
            current = current.getCause();
        }
        return false;
    }

    @Override
    @Transactional
    public void unfollowUser(UUID currentUserId, UUID targetUserId) {
        if (!socialUserRepository.existsByIdAndDeletedAtIsNull(targetUserId)) {
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
        if (!socialUserRepository.existsByIdAndDeletedAtIsNull(requesterId)) {
            throw new AppException(ApiErrorCode.NOT_FOUND, "Requester not found");
        }

        if (!socialUserRepository.existsByIdAndDeletedAtIsNull(currentUserId)) {
            throw new AppException(ApiErrorCode.NOT_FOUND, "Current user not found");
        }

        FollowId followId = new FollowId(requesterId, currentUserId);

        Follow follow =
                followRepository
                        .findByIdAndStatus(followId, FollowStatus.PENDING)
                        .orElseThrow(() -> new AppException(ApiErrorCode.SOCIAL_REQUEST_NOT_FOUND));

        if ("approve".equalsIgnoreCase(action)) {
            follow.setStatus(FollowStatus.ACCEPTED);
            followRepository.save(follow);
            return;
        }

        if ("reject".equalsIgnoreCase(action)) {
            followRepository.delete(follow);
            return;
        }

        throw new AppException(ApiErrorCode.BAD_REQUEST, "Invalid follow request response action");
    }

    @Override
    @Transactional
    public void blockUser(UUID currentUserId, UUID targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new AppException(ApiErrorCode.SOCIAL_SELF_BLOCK);
        }

        if (!socialUserRepository.existsByIdAndDeletedAtIsNull(targetUserId)) {
            throw new AppException(ApiErrorCode.NOT_FOUND, "Target user not found");
        }

        BlockId blockId = new BlockId(currentUserId, targetUserId);

        if (blockRepository.existsById(blockId)) {
            throw new AppException(ApiErrorCode.SOCIAL_ALREADY_BLOCKED);
        }

        Block block = Block.builder().id(blockId).build();
        blockRepository.save(block);

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

        User targetUser =
                socialUserRepository
                        .findByIdAndDeletedAtIsNull(targetUserId)
                        .orElseThrow(
                                () -> new AppException(ApiErrorCode.NOT_FOUND, "User not found"));

        checkCanViewSocialGraph(currentUserId, targetUserId, targetUser);

        int size = normalizeLimit(limit);
        OffsetDateTime cursorTime = decodeCursor(cursor);

        Pageable pageable = PageRequest.of(0, size + 1);

        List<Follow> follows =
                followRepository.findFollowersWithCursor(
                        targetUserId, currentUserId, FollowStatus.ACCEPTED, cursorTime, pageable);

        boolean hasNextPage = follows.size() > size;

        if (hasNextPage) {
            follows = follows.subList(0, size);
        }

        if (follows.isEmpty()) {
            return CursorPageResponse.of(Collections.emptyList(), size, null, null, cursor != null);
        }

        List<UUID> followerIds = follows.stream().map(f -> f.getId().getFollowerId()).toList();

        Map<UUID, User> userMap =
                socialUserRepository.findAllByIdInAndDeletedAtIsNull(followerIds).stream()
                        .collect(Collectors.toMap(User::getId, user -> user));

        List<SocialUserSummaryResponse> content =
                follows.stream()
                        .map(f -> userMap.get(f.getId().getFollowerId()))
                        .filter(user -> user != null)
                        .map(this::toSocialUserSummaryResponse)
                        .toList();

        String startCursor = encodeCursor(follows.get(0).getCreatedAt());
        String endCursor = encodeCursor(follows.get(follows.size() - 1).getCreatedAt());

        return CursorPageResponse.of(content, size, startCursor, endCursor, cursor != null);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<SocialUserSummaryResponse> getFollowing(
            UUID targetUserId, UUID currentUserId, String cursor, int limit) {

        User targetUser =
                socialUserRepository
                        .findByIdAndDeletedAtIsNull(targetUserId)
                        .orElseThrow(
                                () -> new AppException(ApiErrorCode.NOT_FOUND, "User not found"));

        checkCanViewSocialGraph(currentUserId, targetUserId, targetUser);

        int size = normalizeLimit(limit);
        OffsetDateTime cursorTime = decodeCursor(cursor);

        Pageable pageable = PageRequest.of(0, size + 1);

        List<Follow> follows =
                followRepository.findFollowingWithCursor(
                        targetUserId, currentUserId, FollowStatus.ACCEPTED, cursorTime, pageable);

        boolean hasNextPage = follows.size() > size;

        if (hasNextPage) {
            follows = follows.subList(0, size);
        }

        if (follows.isEmpty()) {
            return CursorPageResponse.of(Collections.emptyList(), size, null, null, cursor != null);
        }

        List<UUID> followingIds = follows.stream().map(f -> f.getId().getFollowingId()).toList();

        Map<UUID, User> userMap =
                socialUserRepository.findAllByIdInAndDeletedAtIsNull(followingIds).stream()
                        .collect(Collectors.toMap(User::getId, user -> user));

        List<SocialUserSummaryResponse> content =
                follows.stream()
                        .map(f -> userMap.get(f.getId().getFollowingId()))
                        .filter(user -> user != null)
                        .map(this::toSocialUserSummaryResponse)
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
                socialUserRepository.findAllByIdInAndDeletedAtIsNull(requesterIds).stream()
                        .collect(Collectors.toMap(User::getId, user -> user));

        return pendingFollows.stream()
                .map(
                        follow -> {
                            User user = userMap.get(follow.getId().getFollowerId());

                            if (user == null) {
                                return null;
                            }

                            SocialUserSummaryResponse followerSummary =
                                    toSocialUserSummaryResponse(user);

                            return new FollowRequestResponse(
                                    user.getId(),
                                    followerSummary,
                                    follow.getStatus(),
                                    follow.getCreatedAt());
                        })
                .filter(response -> response != null)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> getAcceptedFollowingExcludingBlocks(UUID viewerId) {
        List<Follow> accepted =
                followRepository.findByIdFollowerIdAndStatus(viewerId, FollowStatus.ACCEPTED);
        if (accepted.isEmpty()) {
            return Collections.emptyList();
        }
        Set<UUID> blockedOrBlocking = new HashSet<>();
        blockRepository
                .findByIdBlockerId(viewerId)
                .forEach(b -> blockedOrBlocking.add(b.getId().getBlockedId()));
        blockRepository
                .findByIdBlockedId(viewerId)
                .forEach(b -> blockedOrBlocking.add(b.getId().getBlockerId()));
        return accepted.stream()
                .map(f -> f.getId().getFollowingId())
                .filter(id -> !blockedOrBlocking.contains(id))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAcceptedFollow(UUID followerId, UUID followingId) {
        return followRepository.existsByIdAndStatus(
                new FollowId(followerId, followingId), FollowStatus.ACCEPTED);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isBlockedBetween(UUID userIdA, UUID userIdB) {
        return blockRepository.existsBetween(userIdA, userIdB);
    }

    private void checkCanViewSocialGraph(UUID currentUserId, UUID targetUserId, User targetUser) {
        if (blockRepository.existsById(new BlockId(currentUserId, targetUserId))
                || blockRepository.existsById(new BlockId(targetUserId, currentUserId))) {
            throw new AppException(ApiErrorCode.SOCIAL_BLOCKED);
        }

        boolean isOwner = currentUserId.equals(targetUserId);

        boolean isAcceptedFollower =
                followRepository.existsByIdAndStatus(
                        new FollowId(currentUserId, targetUserId), FollowStatus.ACCEPTED);

        if (targetUser.isPrivate() && !isOwner && !isAcceptedFollower) {
            throw new AppException(ApiErrorCode.FORBIDDEN, "You cannot view this private account");
        }
    }

    private SocialUserSummaryResponse toSocialUserSummaryResponse(User user) {
        return new SocialUserSummaryResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.isVerified());
    }

    private int normalizeLimit(int limit) {
        return limit > 100 ? 100 : (limit < 1 ? 20 : limit);
    }

    private String encodeCursor(OffsetDateTime time) {
        if (time == null) {
            return null;
        }

        return Base64.getEncoder().encodeToString(time.toString().getBytes());
    }

    private OffsetDateTime decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            // Return a sentinel far in the future so the query condition `createdAt < :cursor`
            // matches all rows on the first page without passing an untyped null to JDBC.
            return OffsetDateTime.now(ZoneOffset.UTC).plusYears(100);
        }

        try {
            String decoded = new String(Base64.getDecoder().decode(cursor));
            return OffsetDateTime.parse(decoded);
        } catch (Exception e) {
            throw new AppException(ApiErrorCode.BAD_REQUEST, "Invalid cursor format");
        }
    }
}
