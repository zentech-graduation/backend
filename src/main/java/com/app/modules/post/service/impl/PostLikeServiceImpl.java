package com.app.modules.post.service.impl;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.outbox.service.OutboxService;
import com.app.common.pagination.Cursor;
import com.app.common.pagination.CursorCodec;
import com.app.common.pagination.TimeCursors;
import com.app.common.response.CursorPageResponse;
import com.app.common.response.UserListItemResponse;
import com.app.common.response.UserSummaryResponse;
import com.app.common.response.ViewerRelationshipResponse;
import com.app.modules.post.dto.response.LikeActionResponse;
import com.app.modules.post.entity.Post;
import com.app.modules.post.entity.PostLike;
import com.app.modules.post.entity.PostLikeId;
import com.app.modules.post.enums.PostStatus;
import com.app.modules.post.messaging.PostEventTypes;
import com.app.modules.post.repository.PostLikeRepository;
import com.app.modules.post.repository.PostRepository;
import com.app.modules.post.service.PostLikeService;
import com.app.modules.post.service.PostVisibilityService;
import com.app.modules.social.service.SocialService;
import com.app.modules.users.service.UserSummaryService;

@Service
public class PostLikeServiceImpl implements PostLikeService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostVisibilityService postVisibilityService;
    private final UserSummaryService userSummaryService;
    private final SocialService socialService;
    private final OutboxService outboxService;

    public PostLikeServiceImpl(
            PostRepository postRepository,
            PostLikeRepository postLikeRepository,
            PostVisibilityService postVisibilityService,
            UserSummaryService userSummaryService,
            SocialService socialService,
            OutboxService outboxService) {
        this.postRepository = postRepository;
        this.postLikeRepository = postLikeRepository;
        this.postVisibilityService = postVisibilityService;
        this.userSummaryService = userSummaryService;
        this.socialService = socialService;
        this.outboxService = outboxService;
    }

    @Override
    @Transactional
    public LikeActionResponse likePost(UUID userId, UUID postId) {
        Post post = fetchVisiblePublishedPost(userId, postId);
        PostLikeId likeId = new PostLikeId(userId, postId);
        if (postLikeRepository.existsById(likeId)) {
            throw new AppException(ApiErrorCode.POST_ALREADY_LIKED);
        }
        // Flush forces the INSERT (and its AFTER INSERT counter trigger) before the scalar
        // re-read; the entity in the persistence context still carries the stale counter.
        try {
            postLikeRepository.saveAndFlush(PostLike.builder().id(likeId).build());
        } catch (DataIntegrityViolationException ex) {
            // A concurrent double-submit lost the insert race; the (user_id, post_id) primary key
            // already recorded the like, so surface the same clean conflict rather than a 500.
            throw new AppException(ApiErrorCode.POST_ALREADY_LIKED);
        }
        outboxService.enqueue(
                PostEventTypes.POST_LIKED_V1,
                PostEventTypes.POST_LIKED_V1,
                "post",
                postId,
                userId,
                Map.of(
                        "postId", postId.toString(),
                        "postOwnerId", post.getUserId().toString(),
                        "userId", userId.toString()));
        return new LikeActionResponse(postId, true, postRepository.findLikeCount(postId));
    }

    @Override
    @Transactional
    public LikeActionResponse unlikePost(UUID userId, UUID postId) {
        Post post =
                postRepository
                        .findByIdAndDeletedAtIsNull(postId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.POST_NOT_FOUND));
        // Unpublished posts surface as not-found to avoid leaking their existence, and a missing
        // like row collapses onto the same code so it cannot serve as a separate oracle.
        if (post.getStatus() != PostStatus.PUBLISHED && !userId.equals(post.getUserId())) {
            throw new AppException(ApiErrorCode.POST_NOT_FOUND);
        }
        // A published post can still be hidden from this viewer by a block, a private owner
        // without an accepted follow, or a soft-deleted owner; apply the same account-level
        // decision likePost uses before mutating the relation.
        if (!postVisibilityService.isVisibleTo(userId, post)) {
            throw new AppException(ApiErrorCode.POST_NOT_FOUND);
        }
        PostLikeId likeId = new PostLikeId(userId, postId);
        PostLike like =
                postLikeRepository
                        .findById(likeId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.POST_NOT_FOUND));
        postLikeRepository.delete(like);
        postLikeRepository.flush();
        return new LikeActionResponse(postId, false, postRepository.findLikeCount(postId));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<UserListItemResponse> listLikers(
            UUID viewerId, UUID postId, String cursor, int size) {
        fetchVisiblePublishedPost(viewerId, postId);
        int pageSize = normalizeLimit(size);
        Cursor decoded = decodeCursor(cursor);
        PageRequest page = PageRequest.of(0, pageSize + 1);
        List<PostLike> likes =
                decoded == null
                        ? postLikeRepository.findFirstLikers(postId, page)
                        : postLikeRepository.findLikersBefore(
                                postId,
                                TimeCursors.fromMicros(decoded.sortValueMicros()),
                                decoded.id(),
                                page);
        boolean hasNextPage = likes.size() > pageSize;
        if (hasNextPage) {
            likes = likes.subList(0, pageSize);
        }
        if (likes.isEmpty()) {
            return CursorPageResponse.of(
                    Collections.emptyList(), false, null, null, cursor != null);
        }
        List<UUID> likerIds = likes.stream().map(l -> l.getId().getUserId()).toList();
        // Batch-resolve every liker; a soft-deleted liker resolves to a placeholder rather than
        // being dropped, so the page size stays consistent with the like count.
        Map<UUID, UserSummaryResponse> summaries = userSummaryService.loadSummaries(likerIds);
        Map<UUID, ViewerRelationshipResponse> relationships =
                socialService.loadRelationships(viewerId, likerIds);
        List<UserListItemResponse> content =
                likerIds.stream()
                        .map(
                                id ->
                                        new UserListItemResponse(
                                                summaries.get(id),
                                                relationships.getOrDefault(
                                                        id, ViewerRelationshipResponse.NONE)))
                        .toList();
        PostLike first = likes.get(0);
        PostLike last = likes.get(likes.size() - 1);
        String startCursor = encodeCursor(first.getCreatedAt(), first.getId().getUserId());
        String endCursor = encodeCursor(last.getCreatedAt(), last.getId().getUserId());
        return CursorPageResponse.of(content, hasNextPage, startCursor, endCursor, cursor != null);
    }

    private Post fetchVisiblePublishedPost(UUID viewerId, UUID postId) {
        Post post =
                postRepository
                        .findByIdAndDeletedAtIsNull(postId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.POST_NOT_FOUND));
        // Unpublished posts surface as not-found to avoid leaking their existence.
        if (post.getStatus() != PostStatus.PUBLISHED && !viewerId.equals(post.getUserId())) {
            throw new AppException(ApiErrorCode.POST_NOT_FOUND);
        }
        if (!postVisibilityService.isVisibleTo(viewerId, post)) {
            throw new AppException(ApiErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private int normalizeLimit(int limit) {
        return limit > MAX_PAGE_SIZE ? MAX_PAGE_SIZE : (limit < 1 ? DEFAULT_PAGE_SIZE : limit);
    }

    private String encodeCursor(OffsetDateTime time, UUID tiebreaker) {
        if (time == null || tiebreaker == null) {
            return null;
        }
        return CursorCodec.encode(new Cursor(TimeCursors.toMicros(time), tiebreaker));
    }

    private Cursor decodeCursor(String cursor) {
        return CursorCodec.decode(cursor);
    }
}
