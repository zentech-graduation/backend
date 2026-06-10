package com.app.modules.post.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.outbox.service.OutboxService;
import com.app.common.security.user.UserPrincipal;
import com.app.common.settings.service.SystemSettingService;
import com.app.modules.hashtag.service.HashtagService;
import com.app.modules.media.entity.MediaAsset;
import com.app.modules.media.enums.MediaType;
import com.app.modules.post.dto.request.CreatePostRequest;
import com.app.modules.post.dto.request.PostStatusTransitionRequest;
import com.app.modules.post.dto.request.UpdatePostCaptionRequest;
import com.app.modules.post.entity.Post;
import com.app.modules.post.entity.PostEditHistory;
import com.app.modules.post.enums.PostStatus;
import com.app.modules.post.enums.PostType;
import com.app.modules.post.mapper.PostMapper;
import com.app.modules.post.repository.PostEditHistoryRepository;
import com.app.modules.post.repository.PostMediaAssetRepository;
import com.app.modules.post.repository.PostRepository;
import com.app.modules.post.repository.PostUserRepository;
import com.app.modules.post.service.PostVisibilityService;
import com.app.modules.social.service.SocialService;

@ExtendWith(MockitoExtension.class)
class PostServiceImplTest {

    @Mock private PostRepository postRepository;
    @Mock private PostEditHistoryRepository postEditHistoryRepository;
    @Mock private PostUserRepository postUserRepository;
    @Mock private PostMediaAssetRepository postMediaAssetRepository;
    @Mock private HashtagService hashtagService;
    @Mock private SystemSettingService systemSettingService;
    @Mock private PostVisibilityService postVisibilityService;
    @Mock private PostResponseAssembler postResponseAssembler;
    @Mock private PostMapper postMapper;
    @Mock private SocialService socialService;
    @Mock private OutboxService outboxService;

    private PostServiceImpl service;

    private final UUID authorId = UUID.randomUUID();
    private final UUID strangerId = UUID.randomUUID();
    private final UUID postId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new PostServiceImpl(
                        postRepository,
                        postEditHistoryRepository,
                        postUserRepository,
                        postMediaAssetRepository,
                        hashtagService,
                        systemSettingService,
                        postVisibilityService,
                        postResponseAssembler,
                        postMapper,
                        socialService,
                        outboxService);
        lenient()
                .when(systemSettingService.getRequiredLong("max_post_media_items"))
                .thenReturn(10L);
        lenient()
                .when(systemSettingService.getRequiredLong("max_hashtags_per_post"))
                .thenReturn(30L);
        lenient().when(hashtagService.getHashtagIdsForPosts(any())).thenReturn(Map.of());
        // Mimic Hibernate id and created_at assignment so publish-time index events carry both.
        lenient()
                .when(postRepository.saveAndFlush(any(Post.class)))
                .thenAnswer(
                        invocation -> {
                            Post p = invocation.getArgument(0);
                            if (p.getId() == null) {
                                p.setId(postId);
                            }
                            if (p.getCreatedAt() == null) {
                                p.setCreatedAt(OffsetDateTime.now());
                            }
                            return p;
                        });
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String role) {
        UserPrincipal principal =
                new UserPrincipal(UUID.randomUUID(), "actor@test.local", role, "ACTIVE");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities()));
    }

    private MediaAsset asset(UUID id, UUID ownerId, MediaType type) {
        return MediaAsset.builder().id(id).userId(ownerId).mediaType(type).build();
    }

    private Post ownedPost(PostStatus status) {
        return Post.builder()
                .id(postId)
                .userId(authorId)
                .status(status)
                .postType(PostType.IMAGE)
                .caption("old caption")
                .createdAt(OffsetDateTime.now())
                .build();
    }

    private CreatePostRequest createRequest(
            String caption, PostType type, List<UUID> mediaIds, PostStatus status) {
        return new CreatePostRequest(caption, type, mediaIds, status, null, null, null);
    }

    @Test
    void createPost_missingMediaAsset_throwsNotFound() {
        UUID mediaId = UUID.randomUUID();
        when(postMediaAssetRepository.findAllById(List.of(mediaId))).thenReturn(List.of());

        assertThatThrownBy(
                        () ->
                                service.createPost(
                                        authorId,
                                        createRequest(
                                                null, PostType.IMAGE, List.of(mediaId), null)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.NOT_FOUND);
    }

    @Test
    void createPost_mediaNotOwnedByAuthor_throwsPostForbidden() {
        UUID mediaId = UUID.randomUUID();
        when(postMediaAssetRepository.findAllById(List.of(mediaId)))
                .thenReturn(List.of(asset(mediaId, strangerId, MediaType.IMAGE)));

        assertThatThrownBy(
                        () ->
                                service.createPost(
                                        authorId,
                                        createRequest(
                                                null, PostType.IMAGE, List.of(mediaId), null)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.POST_FORBIDDEN);
    }

    @Test
    void createPost_carouselWithSingleMedia_throwsBadRequest() {
        UUID mediaId = UUID.randomUUID();
        when(postMediaAssetRepository.findAllById(List.of(mediaId)))
                .thenReturn(List.of(asset(mediaId, authorId, MediaType.IMAGE)));

        assertThatThrownBy(
                        () ->
                                service.createPost(
                                        authorId,
                                        createRequest(
                                                null, PostType.CAROUSEL, List.of(mediaId), null)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.BAD_REQUEST);
    }

    @Test
    void createPost_imageTypeWithVideoAsset_throwsBadRequest() {
        UUID mediaId = UUID.randomUUID();
        when(postMediaAssetRepository.findAllById(List.of(mediaId)))
                .thenReturn(List.of(asset(mediaId, authorId, MediaType.VIDEO)));

        assertThatThrownBy(
                        () ->
                                service.createPost(
                                        authorId,
                                        createRequest(
                                                null, PostType.IMAGE, List.of(mediaId), null)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.BAD_REQUEST);
    }

    @Test
    void createPost_published_extractsHashtagsFromCaption() {
        UUID mediaId = UUID.randomUUID();
        when(postMediaAssetRepository.findAllById(List.of(mediaId)))
                .thenReturn(List.of(asset(mediaId, authorId, MediaType.IMAGE)));

        service.createPost(
                authorId,
                createRequest(
                        "Sunset #Beach and #sunset_2024 vibes",
                        PostType.IMAGE,
                        List.of(mediaId),
                        PostStatus.PUBLISHED));

        verify(hashtagService).upsertHashtagsForPost(postId, List.of("Beach", "sunset_2024"));
        verify(outboxService)
                .enqueue(
                        eq("post.index.upsert.v1"),
                        eq("post.index.upsert.v1"),
                        eq("post"),
                        any(UUID.class),
                        any(),
                        anyMap());
    }

    @Test
    void createPost_draft_skipsHashtagExtraction() {
        UUID mediaId = UUID.randomUUID();
        when(postMediaAssetRepository.findAllById(List.of(mediaId)))
                .thenReturn(List.of(asset(mediaId, authorId, MediaType.IMAGE)));

        service.createPost(
                authorId,
                createRequest("#draft tag", PostType.IMAGE, List.of(mediaId), PostStatus.DRAFT));

        verifyNoInteractions(hashtagService);
    }

    @Test
    void updateCaption_nonOwner_throwsPostForbidden() {
        when(postRepository.findByIdAndDeletedAtIsNull(postId))
                .thenReturn(Optional.of(ownedPost(PostStatus.PUBLISHED)));

        assertThatThrownBy(
                        () ->
                                service.updateCaption(
                                        strangerId, postId, new UpdatePostCaptionRequest("new")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.POST_FORBIDDEN);
        verifyNoInteractions(postEditHistoryRepository);
    }

    @Test
    void updateCaption_owner_appendsEditHistoryWithPreviousCaption() {
        Post post = ownedPost(PostStatus.DRAFT);
        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(post));

        service.updateCaption(authorId, postId, new UpdatePostCaptionRequest("new caption"));

        ArgumentCaptor<PostEditHistory> captor = ArgumentCaptor.forClass(PostEditHistory.class);
        verify(postEditHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getPreviousCaption()).isEqualTo("old caption");
        assertThat(captor.getValue().getEditorId()).isEqualTo(authorId);
        assertThat(captor.getValue().getPostId()).isEqualTo(postId);
        assertThat(post.getCaption()).isEqualTo("new caption");
    }

    @Test
    void updateCaption_publishedPost_refreshesHashtags() {
        Post post = ownedPost(PostStatus.PUBLISHED);
        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(post));

        service.updateCaption(authorId, postId, new UpdatePostCaptionRequest("now with #fresh"));

        InOrder inOrder = Mockito.inOrder(hashtagService);
        inOrder.verify(hashtagService).removeHashtagsForPost(postId);
        inOrder.verify(hashtagService).upsertHashtagsForPost(postId, List.of("fresh"));
    }

    @Test
    void transitionStatus_draftToPublished_upsertsHashtags() {
        Post post = ownedPost(PostStatus.DRAFT);
        post.setCaption("publishing #now");
        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(post));

        service.transitionStatus(
                authorId, postId, new PostStatusTransitionRequest(PostStatus.PUBLISHED));

        assertThat(post.getStatus()).isEqualTo(PostStatus.PUBLISHED);
        verify(hashtagService).upsertHashtagsForPost(postId, List.of("now"));
        verify(outboxService)
                .enqueue(
                        eq("post.index.upsert.v1"),
                        eq("post.index.upsert.v1"),
                        eq("post"),
                        any(UUID.class),
                        any(),
                        anyMap());
    }

    @Test
    void transitionStatus_publishedToArchived_removesHashtags() {
        Post post = ownedPost(PostStatus.PUBLISHED);
        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(post));

        service.transitionStatus(
                authorId, postId, new PostStatusTransitionRequest(PostStatus.ARCHIVED));

        assertThat(post.getStatus()).isEqualTo(PostStatus.ARCHIVED);
        verify(hashtagService).removeHashtagsForPost(postId);
        verify(outboxService)
                .enqueue(
                        eq("post.index.delete.v1"),
                        eq("post.index.delete.v1"),
                        eq("post"),
                        any(UUID.class),
                        any(),
                        anyMap());
    }

    @Test
    void transitionStatus_archivedToPublished_succeeds() {
        Post post = ownedPost(PostStatus.ARCHIVED);
        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(post));

        service.transitionStatus(
                authorId, postId, new PostStatusTransitionRequest(PostStatus.PUBLISHED));

        assertThat(post.getStatus()).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    void transitionStatus_publishedToDraft_throwsBadRequest() {
        when(postRepository.findByIdAndDeletedAtIsNull(postId))
                .thenReturn(Optional.of(ownedPost(PostStatus.PUBLISHED)));

        assertThatThrownBy(
                        () ->
                                service.transitionStatus(
                                        authorId,
                                        postId,
                                        new PostStatusTransitionRequest(PostStatus.DRAFT)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.BAD_REQUEST);
    }

    @Test
    void transitionStatus_toRemovedByAdmin_softDeletes() {
        authenticateAs("ADMIN");
        Post post = ownedPost(PostStatus.PUBLISHED);
        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(post));

        service.transitionStatus(
                strangerId, postId, new PostStatusTransitionRequest(PostStatus.REMOVED));

        assertThat(post.getStatus()).isEqualTo(PostStatus.REMOVED);
        assertThat(post.getDeletedAt()).isNotNull();
        verify(hashtagService).removeHashtagsForPost(postId);
    }

    @Test
    void transitionStatus_toRemovedByStranger_throwsPostForbidden() {
        when(postRepository.findByIdAndDeletedAtIsNull(postId))
                .thenReturn(Optional.of(ownedPost(PostStatus.PUBLISHED)));

        assertThatThrownBy(
                        () ->
                                service.transitionStatus(
                                        strangerId,
                                        postId,
                                        new PostStatusTransitionRequest(PostStatus.REMOVED)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.POST_FORBIDDEN);
    }

    @Test
    void deletePost_owner_setsDeletedAtAndRemovedStatus() {
        Post post = ownedPost(PostStatus.PUBLISHED);
        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(post));

        service.deletePost(authorId, postId);

        assertThat(post.getStatus()).isEqualTo(PostStatus.REMOVED);
        assertThat(post.getDeletedAt()).isNotNull();
        verify(hashtagService).removeHashtagsForPost(postId);
        verify(outboxService)
                .enqueue(
                        eq("post.index.delete.v1"),
                        eq("post.index.delete.v1"),
                        eq("post"),
                        any(UUID.class),
                        any(),
                        anyMap());
    }

    @Test
    void deletePost_nonOwnerNonAdmin_throwsPostForbidden() {
        when(postRepository.findByIdAndDeletedAtIsNull(postId))
                .thenReturn(Optional.of(ownedPost(PostStatus.PUBLISHED)));

        assertThatThrownBy(() -> service.deletePost(strangerId, postId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.POST_FORBIDDEN);
    }

    @Test
    void getPostById_notVisible_throwsPostForbidden() {
        Post post = ownedPost(PostStatus.PUBLISHED);
        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(post));
        when(postVisibilityService.isVisibleTo(strangerId, post)).thenReturn(false);

        assertThatThrownBy(() -> service.getPostById(strangerId, postId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.POST_FORBIDDEN);
    }

    @Test
    void getPostById_missing_throwsPostNotFound() {
        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPostById(strangerId, postId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.POST_NOT_FOUND);
    }

    @Test
    void listEditHistory_nonOwner_throwsPostForbidden() {
        when(postRepository.findByIdAndDeletedAtIsNull(postId))
                .thenReturn(Optional.of(ownedPost(PostStatus.PUBLISHED)));

        assertThatThrownBy(() -> service.listEditHistory(strangerId, postId, null, 20))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.POST_FORBIDDEN);
        verifyNoInteractions(postEditHistoryRepository);
    }

    @Test
    void createPost_duplicateMediaIds_throwsBadRequest() {
        UUID mediaId = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                service.createPost(
                                        authorId,
                                        createRequest(
                                                null,
                                                PostType.CAROUSEL,
                                                List.of(mediaId, mediaId),
                                                null)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.BAD_REQUEST);
        verifyNoInteractions(postMediaAssetRepository);
    }

    @Test
    void createPost_invalidInitialStatus_throwsBadRequest() {
        assertThatThrownBy(
                        () ->
                                service.createPost(
                                        authorId,
                                        createRequest(
                                                null,
                                                PostType.IMAGE,
                                                List.of(UUID.randomUUID()),
                                                PostStatus.ARCHIVED)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.BAD_REQUEST);
        verifyNoInteractions(postMediaAssetRepository);
    }
}
