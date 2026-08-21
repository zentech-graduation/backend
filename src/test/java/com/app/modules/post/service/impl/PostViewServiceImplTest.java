package com.app.modules.post.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.outbox.service.OutboxService;
import com.app.modules.post.dto.response.PostViewResponse;
import com.app.modules.post.entity.Post;
import com.app.modules.post.enums.PostStatus;
import com.app.modules.post.messaging.PostEventTypes;
import com.app.modules.post.repository.PostRepository;
import com.app.modules.post.service.PostVisibilityService;

@ExtendWith(MockitoExtension.class)
class PostViewServiceImplTest {

    @Mock private PostRepository postRepository;
    @Mock private PostVisibilityService postVisibilityService;
    @Mock private OutboxService outboxService;

    private PostViewServiceImpl service;

    private final UUID viewerId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID postId = UUID.randomUUID();

    private Post publishedPost;

    @BeforeEach
    void setUp() {
        service = new PostViewServiceImpl(postRepository, postVisibilityService, outboxService);
        publishedPost =
                Post.builder().id(postId).userId(ownerId).status(PostStatus.PUBLISHED).build();
        lenient()
                .when(postRepository.findByIdAndDeletedAtIsNull(postId))
                .thenReturn(Optional.of(publishedPost));
        lenient().when(postVisibilityService.isVisibleTo(viewerId, publishedPost)).thenReturn(true);
    }

    @Test
    void recordView_visiblePublishedPost_recordsAndEnqueuesEvent() {
        PostViewResponse response = service.recordView(viewerId, postId);

        assertThat(response.postId()).isEqualTo(postId);
        assertThat(response.recorded()).isTrue();
        verify(outboxService)
                .enqueue(
                        eq(PostEventTypes.POST_VIEWED_V1),
                        eq(PostEventTypes.POST_VIEWED_V1),
                        eq("post"),
                        eq(postId),
                        eq(viewerId),
                        eq(
                                Map.of(
                                        "postId", postId.toString(),
                                        "postOwnerId", ownerId.toString(),
                                        "userId", viewerId.toString())));
    }

    @Test
    void recordView_viewerIsOwner_recordedFalseAndDoesNotEnqueue() {
        when(postVisibilityService.isVisibleTo(ownerId, publishedPost)).thenReturn(true);

        PostViewResponse response = service.recordView(ownerId, postId);

        assertThat(response.postId()).isEqualTo(postId);
        assertThat(response.recorded()).isFalse();
        verify(outboxService, never()).enqueue(any(), any(), any(), any(), any(), any());
    }

    @Test
    void recordView_postNotFound_throwsPostNotFound() {
        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordView(viewerId, postId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.POST_NOT_FOUND);
        verify(outboxService, never()).enqueue(any(), any(), any(), any(), any(), any());
    }

    @Test
    void recordView_unpublishedPostNonOwner_throwsPostNotFound() {
        Post draftPost =
                Post.builder()
                        .id(postId)
                        .userId(UUID.randomUUID())
                        .status(PostStatus.DRAFT)
                        .build();
        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(draftPost));

        assertThatThrownBy(() -> service.recordView(viewerId, postId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.POST_NOT_FOUND);
        verify(outboxService, never()).enqueue(any(), any(), any(), any(), any(), any());
    }

    @Test
    void recordView_postNotVisible_throwsPostNotFound() {
        when(postVisibilityService.isVisibleTo(viewerId, publishedPost)).thenReturn(false);

        assertThatThrownBy(() -> service.recordView(viewerId, postId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.POST_NOT_FOUND);
        verify(outboxService, never()).enqueue(any(), any(), any(), any(), any(), any());
    }
}
