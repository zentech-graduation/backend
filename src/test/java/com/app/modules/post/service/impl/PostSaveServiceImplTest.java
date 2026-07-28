package com.app.modules.post.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.response.CursorPageResponse;
import com.app.modules.post.dto.response.PostResponse;
import com.app.modules.post.dto.response.SavedPostResponse;
import com.app.modules.post.entity.Post;
import com.app.modules.post.entity.PostSave;
import com.app.modules.post.entity.PostSaveId;
import com.app.modules.post.enums.PostStatus;
import com.app.modules.post.repository.PostRepository;
import com.app.modules.post.repository.PostSaveRepository;
import com.app.modules.post.service.PostVisibilityService;

@ExtendWith(MockitoExtension.class)
class PostSaveServiceImplTest {

    @Mock private PostRepository postRepository;
    @Mock private PostSaveRepository postSaveRepository;
    @Mock private PostVisibilityService postVisibilityService;
    @Mock private PostResponseAssembler postResponseAssembler;

    private PostSaveServiceImpl service;

    private final UUID userId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID postId = UUID.randomUUID();
    private final PostSaveId saveId = new PostSaveId(userId, postId);

    private Post publishedPost;

    @BeforeEach
    void setUp() {
        service =
                new PostSaveServiceImpl(
                        postRepository,
                        postSaveRepository,
                        postVisibilityService,
                        postResponseAssembler);
        publishedPost =
                Post.builder().id(postId).userId(ownerId).status(PostStatus.PUBLISHED).build();
        lenient()
                .when(postRepository.findByIdAndDeletedAtIsNull(postId))
                .thenReturn(Optional.of(publishedPost));
        lenient().when(postVisibilityService.isVisibleTo(userId, publishedPost)).thenReturn(true);
        // Mirror the assembler contract: one response per input post, order preserved.
        lenient()
                .when(postResponseAssembler.assemble(eq(userId), anyList()))
                .thenAnswer(
                        invocation ->
                                Collections.nCopies(
                                        ((List<?>) invocation.getArgument(1)).size(),
                                        (PostResponse) null));
    }

    private PostSave save(UUID savedPostId) {
        return PostSave.builder().id(new PostSaveId(userId, savedPostId)).build();
    }

    @Test
    void savePost_alreadySaved_throwsPostAlreadySaved() {
        when(postSaveRepository.existsById(saveId)).thenReturn(true);

        assertThatThrownBy(() -> service.savePost(userId, postId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.POST_ALREADY_SAVED);
        verify(postSaveRepository, never()).saveAndFlush(any());
    }

    @Test
    void savePost_firstSave_persists() {
        when(postSaveRepository.existsById(saveId)).thenReturn(false);

        service.savePost(userId, postId);

        verify(postSaveRepository).saveAndFlush(any(PostSave.class));
    }

    @Test
    void savePost_concurrentDuplicateInsert_throwsAlreadySaved() {
        when(postSaveRepository.existsById(saveId)).thenReturn(false);
        when(postSaveRepository.saveAndFlush(any(PostSave.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.savePost(userId, postId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.POST_ALREADY_SAVED);
    }

    @Test
    void unsavePost_notSaved_throwsPostNotFound() {
        when(postSaveRepository.findById(saveId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unsavePost(userId, postId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.POST_NOT_FOUND);
    }

    @Test
    void unsavePost_hiddenPostNonOwner_throwsPostNotFound() {
        Post draftPost =
                Post.builder()
                        .id(postId)
                        .userId(UUID.randomUUID())
                        .status(PostStatus.DRAFT)
                        .build();
        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(draftPost));

        assertThatThrownBy(() -> service.unsavePost(userId, postId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.POST_NOT_FOUND);
        verify(postSaveRepository, never()).findById(any());
        verify(postSaveRepository, never()).delete(any());
    }

    @Test
    void unsavePost_publishedPostNotVisible_throwsPostNotFound() {
        when(postVisibilityService.isVisibleTo(userId, publishedPost)).thenReturn(false);

        assertThatThrownBy(() -> service.unsavePost(userId, postId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.POST_NOT_FOUND);
        verify(postSaveRepository, never()).findById(any());
        verify(postSaveRepository, never()).delete(any());
    }

    @Test
    void unsavePost_hiddenPostOwner_reachesSaveLookup() {
        Post draftPost = Post.builder().id(postId).userId(userId).status(PostStatus.DRAFT).build();
        PostSave existing = PostSave.builder().id(saveId).build();
        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(draftPost));
        when(postVisibilityService.isVisibleTo(userId, draftPost)).thenReturn(true);
        when(postSaveRepository.findById(saveId)).thenReturn(Optional.of(existing));

        service.unsavePost(userId, postId);

        verify(postSaveRepository).delete(existing);
    }

    @Test
    void listSavedPosts_postNoLongerVisible_excludedFromResults() {
        UUID hiddenPostId = UUID.randomUUID();
        Post hiddenPost =
                Post.builder()
                        .id(hiddenPostId)
                        .userId(UUID.randomUUID())
                        .status(PostStatus.PUBLISHED)
                        .build();
        when(postSaveRepository.findFirstSaves(eq(userId), any(Pageable.class)))
                .thenReturn(List.of(save(postId), save(hiddenPostId)));
        when(postRepository.findAllById(List.of(postId, hiddenPostId)))
                .thenReturn(List.of(publishedPost, hiddenPost));
        when(postVisibilityService.isVisibleTo(userId, hiddenPost)).thenReturn(false);

        CursorPageResponse<SavedPostResponse> page = service.listSavedPosts(userId, null, 20);

        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void listSavedPosts_softDeletedPost_excluded() {
        UUID deletedPostId = UUID.randomUUID();
        when(postSaveRepository.findFirstSaves(eq(userId), any(Pageable.class)))
                .thenReturn(List.of(save(postId), save(deletedPostId)));
        // The @SQLRestriction filter drops the soft-deleted row from findAllById.
        when(postRepository.findAllById(List.of(postId, deletedPostId)))
                .thenReturn(List.of(publishedPost));

        CursorPageResponse<SavedPostResponse> page = service.listSavedPosts(userId, null, 20);

        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void savePost_postNotFound_throwsPostNotFound() {
        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.savePost(userId, postId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.POST_NOT_FOUND);
    }

    @Test
    void savePost_unpublishedPostNonOwner_throwsPostNotFound() {
        Post draftPost =
                Post.builder()
                        .id(postId)
                        .userId(UUID.randomUUID())
                        .status(PostStatus.DRAFT)
                        .build();
        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(draftPost));

        assertThatThrownBy(() -> service.savePost(userId, postId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.POST_NOT_FOUND);
    }

    @Test
    void savePost_postNotVisible_throwsPostNotFound() {
        when(postVisibilityService.isVisibleTo(userId, publishedPost)).thenReturn(false);

        assertThatThrownBy(() -> service.savePost(userId, postId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.POST_NOT_FOUND);
    }

    @Test
    void listSavedPosts_invalidCursor_throwsInvalidCursor() {
        assertThatThrownBy(() -> service.listSavedPosts(userId, "!!!invalid-cursor!!!", 20))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.INVALID_CURSOR);
    }

    @Test
    void unsavePost_postNotFound_throwsPostNotFound() {
        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unsavePost(userId, postId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.POST_NOT_FOUND);
    }
}
