package com.app.modules.post.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.messaging.RecommendationInteractionContract;
import com.app.common.outbox.service.OutboxService;
import com.app.common.response.CursorPageResponse;
import com.app.modules.post.dto.response.PostResponse;
import com.app.modules.post.dto.response.SavedPostResponse;
import com.app.modules.post.entity.Post;
import com.app.modules.post.entity.PostSave;
import com.app.modules.post.entity.PostSaveId;
import com.app.modules.post.enums.PostStatus;
import com.app.modules.post.repository.PostRepository;
import com.app.modules.post.repository.PostSaveRepository;
import com.app.modules.post.service.PostSaveService;
import com.app.modules.post.service.PostVisibilityService;

@Service
public class PostSaveServiceImpl implements PostSaveService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final PostRepository postRepository;
    private final PostSaveRepository postSaveRepository;
    private final PostVisibilityService postVisibilityService;
    private final PostResponseAssembler postResponseAssembler;
    private final OutboxService outboxService;

    public PostSaveServiceImpl(
            PostRepository postRepository,
            PostSaveRepository postSaveRepository,
            PostVisibilityService postVisibilityService,
            PostResponseAssembler postResponseAssembler,
            OutboxService outboxService) {
        this.postRepository = postRepository;
        this.postSaveRepository = postSaveRepository;
        this.postVisibilityService = postVisibilityService;
        this.postResponseAssembler = postResponseAssembler;
        this.outboxService = outboxService;
    }

    @Override
    @Transactional
    public void savePost(UUID userId, UUID postId) {
        Post post =
                postRepository
                        .findByIdAndDeletedAtIsNull(postId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.POST_NOT_FOUND));
        // Unpublished posts surface as not-found to avoid leaking their existence.
        if (post.getStatus() != PostStatus.PUBLISHED && !userId.equals(post.getUserId())) {
            throw new AppException(ApiErrorCode.POST_NOT_FOUND);
        }
        if (!postVisibilityService.isVisibleTo(userId, post)) {
            throw new AppException(ApiErrorCode.POST_FORBIDDEN);
        }
        PostSaveId saveId = new PostSaveId(userId, postId);
        if (postSaveRepository.existsById(saveId)) {
            throw new AppException(ApiErrorCode.POST_ALREADY_SAVED);
        }
        postSaveRepository.save(PostSave.builder().id(saveId).build());
        enqueueInteraction("post_save", post, userId);
    }

    @Override
    @Transactional
    public void unsavePost(UUID userId, UUID postId) {
        Post post =
                postRepository
                        .findByIdAndDeletedAtIsNull(postId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.POST_NOT_FOUND));
        PostSaveId saveId = new PostSaveId(userId, postId);
        PostSave save =
                postSaveRepository
                        .findById(saveId)
                        .orElseThrow(
                                () -> new AppException(ApiErrorCode.NOT_FOUND, "Save not found"));
        postSaveRepository.delete(save);
        enqueueInteraction("post_unsave", post, userId);
    }

    private void enqueueInteraction(String eventType, Post post, UUID userId) {
        Map<String, Object> data = new HashMap<>();
        data.put("eventType", eventType);
        data.put("entityType", "post");
        data.put("entityId", post.getId().toString());
        data.put("targetUserId", post.getUserId().toString());
        outboxService.enqueue(
                RecommendationInteractionContract.REC_INTERACTION_RECORDED_V1,
                RecommendationInteractionContract.REC_INTERACTION_RECORDED_V1,
                "post",
                post.getId(),
                userId,
                data);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<SavedPostResponse> listSavedPosts(
            UUID userId, String cursor, int size) {
        int pageSize = normalizeLimit(size);
        OffsetDateTime cursorTime = decodeCursor(cursor);
        PageRequest page = PageRequest.of(0, pageSize + 1);
        List<PostSave> saves =
                cursorTime == null
                        ? postSaveRepository.findFirstSaves(userId, page)
                        : postSaveRepository.findSavesBefore(userId, cursorTime, page);
        if (saves.size() > pageSize) {
            saves = saves.subList(0, pageSize);
        }
        if (saves.isEmpty()) {
            return CursorPageResponse.of(
                    Collections.emptyList(), pageSize, null, null, cursor != null);
        }
        List<UUID> postIds = saves.stream().map(s -> s.getId().getPostId()).toList();
        // findAllById drops soft-deleted posts via the entity's @SQLRestriction filter.
        Map<UUID, Post> posts =
                postRepository.findAllById(postIds).stream()
                        .filter(p -> p.getStatus() == PostStatus.PUBLISHED)
                        .filter(p -> postVisibilityService.isVisibleTo(userId, p))
                        .collect(Collectors.toMap(Post::getId, Function.identity()));
        // Post-fetch filtering can shrink a page below the requested size; cursors stay correct
        // because they encode post_saves.created_at, not row counts.
        List<PostSave> visibleSaves =
                saves.stream().filter(s -> posts.containsKey(s.getId().getPostId())).toList();
        List<Post> orderedPosts =
                visibleSaves.stream().map(s -> posts.get(s.getId().getPostId())).toList();
        List<PostResponse> responses = postResponseAssembler.assemble(orderedPosts);
        List<SavedPostResponse> content = new ArrayList<>(visibleSaves.size());
        for (int i = 0; i < visibleSaves.size(); i++) {
            content.add(
                    new SavedPostResponse(responses.get(i), visibleSaves.get(i).getCreatedAt()));
        }
        String startCursor = encodeCursor(saves.get(0).getCreatedAt());
        String endCursor = encodeCursor(saves.get(saves.size() - 1).getCreatedAt());
        return CursorPageResponse.of(content, pageSize, startCursor, endCursor, cursor != null);
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
