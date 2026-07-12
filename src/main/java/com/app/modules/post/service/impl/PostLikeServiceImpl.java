package com.app.modules.post.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.response.CursorPageResponse;
import com.app.modules.post.dto.response.LikeActionResponse;
import com.app.modules.post.dto.response.LikerResponse;
import com.app.modules.post.entity.Post;
import com.app.modules.post.entity.PostLike;
import com.app.modules.post.entity.PostLikeId;
import com.app.modules.post.enums.PostStatus;
import com.app.modules.post.mapper.PostMapper;
import com.app.modules.post.repository.PostLikeRepository;
import com.app.modules.post.repository.PostRepository;
import com.app.modules.post.repository.PostUserRepository;
import com.app.modules.post.service.PostLikeService;
import com.app.modules.post.service.PostVisibilityService;
import com.app.modules.users.entity.User;

@Service
public class PostLikeServiceImpl implements PostLikeService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostUserRepository postUserRepository;
    private final PostVisibilityService postVisibilityService;
    private final PostMapper postMapper;

    public PostLikeServiceImpl(
            PostRepository postRepository,
            PostLikeRepository postLikeRepository,
            PostUserRepository postUserRepository,
            PostVisibilityService postVisibilityService,
            PostMapper postMapper) {
        this.postRepository = postRepository;
        this.postLikeRepository = postLikeRepository;
        this.postUserRepository = postUserRepository;
        this.postVisibilityService = postVisibilityService;
        this.postMapper = postMapper;
    }

    @Override
    @Transactional
    public LikeActionResponse likePost(UUID userId, UUID postId) {
        fetchVisiblePublishedPost(userId, postId);
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
        return new LikeActionResponse(postId, true, postRepository.findLikeCount(postId));
    }

    @Override
    @Transactional
    public LikeActionResponse unlikePost(UUID userId, UUID postId) {
        postRepository
                .findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new AppException(ApiErrorCode.POST_NOT_FOUND));
        PostLikeId likeId = new PostLikeId(userId, postId);
        PostLike like =
                postLikeRepository
                        .findById(likeId)
                        .orElseThrow(
                                () -> new AppException(ApiErrorCode.NOT_FOUND, "Like not found"));
        postLikeRepository.delete(like);
        postLikeRepository.flush();
        return new LikeActionResponse(postId, false, postRepository.findLikeCount(postId));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<LikerResponse> listLikers(
            UUID viewerId, UUID postId, String cursor, int size) {
        fetchVisiblePublishedPost(viewerId, postId);
        int pageSize = normalizeLimit(size);
        OffsetDateTime cursorTime = decodeCursor(cursor);
        PageRequest page = PageRequest.of(0, pageSize + 1);
        List<PostLike> likes =
                cursorTime == null
                        ? postLikeRepository.findFirstLikers(postId, page)
                        : postLikeRepository.findLikersBefore(postId, cursorTime, page);
        if (likes.size() > pageSize) {
            likes = likes.subList(0, pageSize);
        }
        if (likes.isEmpty()) {
            return CursorPageResponse.of(
                    Collections.emptyList(), pageSize, null, null, cursor != null);
        }
        List<UUID> likerIds = likes.stream().map(l -> l.getId().getUserId()).toList();
        Map<UUID, User> users =
                postUserRepository.findAllByIdInAndDeletedAtIsNull(likerIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        List<LikerResponse> content =
                likes.stream()
                        .map(l -> users.get(l.getId().getUserId()))
                        .filter(user -> user != null)
                        .map(postMapper::toLikerResponse)
                        .toList();
        String startCursor = encodeCursor(likes.get(0).getCreatedAt());
        String endCursor = encodeCursor(likes.get(likes.size() - 1).getCreatedAt());
        return CursorPageResponse.of(content, pageSize, startCursor, endCursor, cursor != null);
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
            throw new AppException(ApiErrorCode.POST_FORBIDDEN);
        }
        return post;
    }

    private int normalizeLimit(int limit) {
        return limit > MAX_PAGE_SIZE ? MAX_PAGE_SIZE : (limit < 1 ? DEFAULT_PAGE_SIZE : limit);
    }

    private String encodeCursor(OffsetDateTime time) {
        if (time == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(time.toString().getBytes(StandardCharsets.UTF_8));
    }

    private OffsetDateTime decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(
                    new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AppException(ApiErrorCode.BAD_REQUEST, "Invalid cursor format");
        }
    }
}
