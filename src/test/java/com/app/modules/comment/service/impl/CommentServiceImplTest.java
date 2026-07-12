package com.app.modules.comment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.outbox.service.OutboxService;
import com.app.common.response.CursorPageResponse;
import com.app.common.security.user.UserPrincipal;
import com.app.modules.comment.config.CommentProperties;
import com.app.modules.comment.dto.request.CreateCommentRequest;
import com.app.modules.comment.dto.request.EditCommentRequest;
import com.app.modules.comment.dto.response.CommentResponse;
import com.app.modules.comment.entity.Comment;
import com.app.modules.comment.entity.CommentWriteIdempotency;
import com.app.modules.comment.mapper.CommentMapper;
import com.app.modules.comment.messaging.CommentEventTypes;
import com.app.modules.comment.observability.CommentMetrics;
import com.app.modules.comment.repository.CommentIdempotencyRepository;
import com.app.modules.comment.repository.CommentLikeRepository;
import com.app.modules.comment.repository.CommentRepository;
import com.app.modules.comment.repository.CommentUserRepository;
import com.app.modules.comment.service.CommentAccessPolicyService;
import com.app.modules.comment.service.CommentCacheService;
import com.app.modules.comment.service.CommentModerationService;
import com.app.modules.comment.service.CommentModerationService.ModerationResult;
import com.app.modules.post.entity.Post;
import com.app.modules.post.enums.PostStatus;
import com.app.modules.post.repository.PostRepository;
import com.app.modules.post.service.PostVisibilityService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @Mock private CommentRepository commentRepository;
    @Mock private CommentLikeRepository commentLikeRepository;
    @Mock private CommentIdempotencyRepository idempotencyRepository;
    @Mock private PostRepository postRepository;
    @Mock private CommentUserRepository commentUserRepository;
    @Mock private CommentModerationService moderationService;
    @Mock private CommentAccessPolicyService accessPolicy;
    @Mock private CommentMapper mapper;
    @Mock private OutboxService outboxService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private CommentCacheService cacheService;
    @Mock private CommentMetrics metrics;
    @Mock private PostVisibilityService postVisibilityService;

    private CommentServiceImpl service;

    private final UUID actorId = UUID.randomUUID();
    private final UUID postId = UUID.randomUUID();
    private final UUID commentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new CommentServiceImpl(
                        commentRepository,
                        commentLikeRepository,
                        idempotencyRepository,
                        postRepository,
                        commentUserRepository,
                        moderationService,
                        accessPolicy,
                        mapper,
                        outboxService,
                        new CommentProperties(0, 24, java.util.List.of()),
                        redisTemplate,
                        objectMapper,
                        cacheService,
                        metrics,
                        postVisibilityService);
        lenient()
                .when(metrics.createLatency())
                .thenReturn(new SimpleMeterRegistry().timer("comment.create.latency"));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Post publishedPost() {
        return Post.builder()
                .id(postId)
                .userId(UUID.randomUUID())
                .status(PostStatus.PUBLISHED)
                .build();
    }

    private CreateCommentRequest createRequest(UUID parentId) {
        return new CreateCommentRequest(postId, parentId, "hello world");
    }

    @Test
    void createComment_postNotFound_throwsNotFound() {
        when(postRepository.findById(postId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createComment(actorId, createRequest(null), null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.POST_NOT_FOUND);
    }

    @Test
    void createComment_postNotPublished_throwsForbidden() {
        Post draft =
                Post.builder()
                        .id(postId)
                        .userId(UUID.randomUUID())
                        .status(PostStatus.DRAFT)
                        .build();
        when(postRepository.findById(postId)).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.createComment(actorId, createRequest(null), null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.POST_FORBIDDEN);
    }

    @Test
    void createComment_accessDenied_throwsRestricted() {
        when(postRepository.findById(postId)).thenReturn(Optional.of(publishedPost()));
        org.mockito.Mockito.doThrow(new AppException(ApiErrorCode.POST_COMMENTING_RESTRICTED))
                .when(accessPolicy)
                .assertCanComment(eq(actorId), any());
        assertThatThrownBy(() -> service.createComment(actorId, createRequest(null), null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.POST_COMMENTING_RESTRICTED);
    }

    @Test
    void createComment_moderationRejected_throwsModerationRejected() {
        when(postRepository.findById(postId)).thenReturn(Optional.of(publishedPost()));
        when(moderationService.check(any())).thenReturn(ModerationResult.rejected("spam"));
        assertThatThrownBy(() -> service.createComment(actorId, createRequest(null), null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.COMMENT_MODERATION_REJECTED);
        verify(outboxService, never()).enqueue(any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    void createComment_depthExceeded_throwsDepthExceeded() {
        UUID parentId = UUID.randomUUID();
        when(postRepository.findById(postId)).thenReturn(Optional.of(publishedPost()));
        Comment parent = Comment.builder().id(parentId).postId(postId).depth((short) 10).build();
        when(commentRepository.findByIdAndDeletedAtIsNull(parentId))
                .thenReturn(Optional.of(parent));
        assertThatThrownBy(() -> service.createComment(actorId, createRequest(parentId), null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.COMMENT_DEPTH_EXCEEDED);
    }

    @Test
    void createComment_success_persistsAndEnqueuesOutbox() {
        when(postRepository.findById(postId)).thenReturn(Optional.of(publishedPost()));
        when(moderationService.check(any())).thenReturn(ModerationResult.approved());
        Comment saved =
                Comment.builder()
                        .id(commentId)
                        .postId(postId)
                        .userId(actorId)
                        .depth((short) 0)
                        .build();
        when(commentRepository.save(any())).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(sampleResponse());

        CommentResponse response = service.createComment(actorId, createRequest(null), null);

        assertThat(response).isNotNull();
        verify(outboxService)
                .enqueue(
                        eq(CommentEventTypes.COMMENT_CREATED_V1),
                        eq(CommentEventTypes.COMMENT_CREATED_V1),
                        eq("comment"),
                        eq(commentId),
                        eq(actorId),
                        anyMap());
        verify(cacheService).pushToFront(eq(postId), any());
    }

    @Test
    void createComment_idempotencyReplay_returnsCachedResponse() {
        Post post = publishedPost();
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(moderationService.check(any())).thenReturn(ModerationResult.approved());
        String hash = sha256(post.getId() + "|null|hello world");
        when(idempotencyRepository.insertIfAbsent(eq(actorId), eq("key-1"), eq(hash)))
                .thenReturn(0);
        CommentWriteIdempotency row =
                CommentWriteIdempotency.builder()
                        .userId(actorId)
                        .idempotencyKey("key-1")
                        .requestHash(hash)
                        .responseBody("cached-json")
                        .build();
        when(idempotencyRepository.findByUserIdAndIdempotencyKey(actorId, "key-1"))
                .thenReturn(Optional.of(row));
        CommentResponse cached = sampleResponse();
        when(objectMapper.readValue("cached-json", CommentResponse.class)).thenReturn(cached);

        CommentResponse response = service.createComment(actorId, createRequest(null), "key-1");

        assertThat(response).isSameAs(cached);
        verify(commentRepository, never()).save(any());
        verify(outboxService, never()).enqueue(any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    void createComment_idempotencyConflict_throwsConflict() {
        when(postRepository.findById(postId)).thenReturn(Optional.of(publishedPost()));
        when(moderationService.check(any())).thenReturn(ModerationResult.approved());
        when(idempotencyRepository.insertIfAbsent(eq(actorId), eq("key-2"), any())).thenReturn(0);
        CommentWriteIdempotency row =
                CommentWriteIdempotency.builder()
                        .userId(actorId)
                        .idempotencyKey("key-2")
                        .requestHash("a-different-hash")
                        .responseBody("cached-json")
                        .build();
        when(idempotencyRepository.findByUserIdAndIdempotencyKey(actorId, "key-2"))
                .thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.createComment(actorId, createRequest(null), "key-2"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.COMMENT_IDEMPOTENCY_CONFLICT);
        verify(commentRepository, never()).save(any());
    }

    @Test
    void editComment_notOwner_throwsForbidden() {
        Comment comment =
                Comment.builder().id(commentId).postId(postId).userId(UUID.randomUUID()).build();
        when(commentRepository.findByIdAndDeletedAtIsNull(commentId))
                .thenReturn(Optional.of(comment));
        assertThatThrownBy(
                        () ->
                                service.editComment(
                                        actorId, commentId, new EditCommentRequest("new")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.COMMENT_FORBIDDEN);
    }

    @Test
    void editComment_success_enqueuesEditedEvent() {
        Comment comment = Comment.builder().id(commentId).postId(postId).userId(actorId).build();
        when(commentRepository.findByIdAndDeletedAtIsNull(commentId))
                .thenReturn(Optional.of(comment));
        when(moderationService.check(any())).thenReturn(ModerationResult.approved());
        when(commentRepository.save(any())).thenReturn(comment);
        when(mapper.toResponse(comment)).thenReturn(sampleResponse());

        service.editComment(actorId, commentId, new EditCommentRequest("updated"));

        verify(outboxService)
                .enqueue(
                        eq(CommentEventTypes.COMMENT_EDITED_V1),
                        eq(CommentEventTypes.COMMENT_EDITED_V1),
                        eq("comment"),
                        eq(commentId),
                        eq(actorId),
                        anyMap());
        verify(cacheService).invalidate(postId);
    }

    @Test
    void deleteComment_notOwner_throwsForbidden() {
        Comment comment =
                Comment.builder().id(commentId).postId(postId).userId(UUID.randomUUID()).build();
        when(commentRepository.findByIdAndDeletedAtIsNull(commentId))
                .thenReturn(Optional.of(comment));
        assertThatThrownBy(() -> service.deleteComment(actorId, commentId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.COMMENT_FORBIDDEN);
    }

    @Test
    void deleteComment_success_callsSoftDeleteSubtree() {
        Comment comment = Comment.builder().id(commentId).postId(postId).userId(actorId).build();
        when(commentRepository.findByIdAndDeletedAtIsNull(commentId))
                .thenReturn(Optional.of(comment));

        service.deleteComment(actorId, commentId);

        verify(commentRepository).softDeleteSubtree(eq(commentId), any());
        verify(outboxService)
                .enqueue(
                        eq(CommentEventTypes.COMMENT_DELETED_V1),
                        any(),
                        any(),
                        eq(commentId),
                        eq(actorId),
                        anyMap());
        verify(cacheService).invalidate(postId);
    }

    @Test
    void deleteComment_admin_allowed() {
        UUID adminId = UUID.randomUUID();
        UserPrincipal admin = new UserPrincipal(adminId, "admin@test", "ADMIN", "ACTIVE");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                admin, null, admin.getAuthorities()));
        Comment comment =
                Comment.builder().id(commentId).postId(postId).userId(UUID.randomUUID()).build();
        when(commentRepository.findByIdAndDeletedAtIsNull(commentId))
                .thenReturn(Optional.of(comment));

        service.deleteComment(adminId, commentId);

        verify(commentRepository).softDeleteSubtree(eq(commentId), any());
    }

    @Test
    void likeComment_ownComment_throwsForbidden() {
        Comment comment = Comment.builder().id(commentId).postId(postId).userId(actorId).build();
        when(commentRepository.findByIdAndDeletedAtIsNull(commentId))
                .thenReturn(Optional.of(comment));
        assertThatThrownBy(() -> service.likeComment(actorId, commentId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.COMMENT_FORBIDDEN);
    }

    @Test
    void likeComment_alreadyLiked_throwsConflict() {
        Comment comment =
                Comment.builder().id(commentId).postId(postId).userId(UUID.randomUUID()).build();
        when(commentRepository.findByIdAndDeletedAtIsNull(commentId))
                .thenReturn(Optional.of(comment));
        when(commentLikeRepository.existsByIdUserIdAndIdCommentId(actorId, commentId))
                .thenReturn(true);
        assertThatThrownBy(() -> service.likeComment(actorId, commentId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.COMMENT_ALREADY_LIKED);
    }

    @Test
    void likeComment_success_persistsAndEnqueues() {
        Comment comment =
                Comment.builder().id(commentId).postId(postId).userId(UUID.randomUUID()).build();
        when(commentRepository.findByIdAndDeletedAtIsNull(commentId))
                .thenReturn(Optional.of(comment));
        when(commentLikeRepository.existsByIdUserIdAndIdCommentId(actorId, commentId))
                .thenReturn(false);

        service.likeComment(actorId, commentId);

        verify(commentLikeRepository).saveAndFlush(any());
        verify(outboxService)
                .enqueue(
                        eq(CommentEventTypes.COMMENT_LIKED_V1),
                        any(),
                        any(),
                        eq(commentId),
                        eq(actorId),
                        anyMap());
    }

    @Test
    void likeComment_concurrentDuplicateInsert_throwsAlreadyLiked() {
        Comment comment =
                Comment.builder().id(commentId).postId(postId).userId(UUID.randomUUID()).build();
        when(commentRepository.findByIdAndDeletedAtIsNull(commentId))
                .thenReturn(Optional.of(comment));
        when(commentLikeRepository.existsByIdUserIdAndIdCommentId(actorId, commentId))
                .thenReturn(false);
        when(commentLikeRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.likeComment(actorId, commentId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.COMMENT_ALREADY_LIKED);
        verify(outboxService, never()).enqueue(any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    void unlikeComment_notLiked_throwsConflict() {
        Comment comment =
                Comment.builder().id(commentId).postId(postId).userId(UUID.randomUUID()).build();
        when(commentRepository.findByIdAndDeletedAtIsNull(commentId))
                .thenReturn(Optional.of(comment));
        when(commentLikeRepository.deleteByUserAndComment(actorId, commentId)).thenReturn(0);
        assertThatThrownBy(() -> service.unlikeComment(actorId, commentId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.COMMENT_NOT_LIKED);
    }

    @Test
    void unlikeComment_success_enqueues() {
        Comment comment =
                Comment.builder().id(commentId).postId(postId).userId(UUID.randomUUID()).build();
        when(commentRepository.findByIdAndDeletedAtIsNull(commentId))
                .thenReturn(Optional.of(comment));
        when(commentLikeRepository.deleteByUserAndComment(actorId, commentId)).thenReturn(1);

        service.unlikeComment(actorId, commentId);

        verify(outboxService)
                .enqueue(
                        eq(CommentEventTypes.COMMENT_UNLIKED_V1),
                        any(),
                        any(),
                        eq(commentId),
                        eq(actorId),
                        anyMap());
    }

    @Test
    void listTopLevelComments_exactlyFullPage_hasNextPageFalse() {
        int limit = 3;
        when(postRepository.findById(postId)).thenReturn(Optional.of(publishedPost()));
        when(postVisibilityService.isVisibleTo(eq(actorId), any())).thenReturn(true);
        List<Comment> rows =
                List.of(
                        Comment.builder().id(UUID.randomUUID()).postId(postId).build(),
                        Comment.builder().id(UUID.randomUUID()).postId(postId).build(),
                        Comment.builder().id(UUID.randomUUID()).postId(postId).build());
        when(commentRepository.findFirstTopLevel(eq(postId), any())).thenReturn(rows);
        when(mapper.toResponse(any())).thenReturn(sampleResponse());

        CursorPageResponse<CommentResponse> result =
                service.listTopLevelComments(actorId, postId, null, limit);

        assertThat(result.getPageInfo().isHasNextPage()).isFalse();
        assertThat(result.getContent()).hasSize(3);
    }

    @Test
    void listTopLevelComments_oneMoreThanLimit_hasNextPageTrue() {
        int limit = 3;
        when(postRepository.findById(postId)).thenReturn(Optional.of(publishedPost()));
        when(postVisibilityService.isVisibleTo(eq(actorId), any())).thenReturn(true);
        List<Comment> rows =
                List.of(
                        Comment.builder().id(UUID.randomUUID()).postId(postId).build(),
                        Comment.builder().id(UUID.randomUUID()).postId(postId).build(),
                        Comment.builder().id(UUID.randomUUID()).postId(postId).build(),
                        Comment.builder().id(UUID.randomUUID()).postId(postId).build());
        when(commentRepository.findFirstTopLevel(eq(postId), any())).thenReturn(rows);
        when(mapper.toResponse(any())).thenReturn(sampleResponse());

        CursorPageResponse<CommentResponse> result =
                service.listTopLevelComments(actorId, postId, null, limit);

        assertThat(result.getPageInfo().isHasNextPage()).isTrue();
        assertThat(result.getContent()).hasSize(3);
    }

    private CommentResponse sampleResponse() {
        return new CommentResponse(
                commentId, postId, actorId, null, null, (short) 0, "hello world", 0, 0, null, null);
    }

    // Mirrors CommentServiceImpl's request-hash formula so the replay test can match the stored
    // row.
    private static String sha256(String input) {
        try {
            byte[] hash =
                    MessageDigest.getInstance("SHA-256")
                            .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
