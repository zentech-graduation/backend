package com.app.modules.story.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.story.dto.response.StoryLikeActionResponse;
import com.app.modules.story.entity.Story;
import com.app.modules.story.entity.StoryLike;
import com.app.modules.story.entity.StoryLikeId;
import com.app.modules.story.repository.StoryLikeRepository;
import com.app.modules.story.repository.StoryRepository;
import com.app.modules.story.service.StoryVisibilityService;

@ExtendWith(MockitoExtension.class)
class StoryLikeServiceImplTest {

    @Mock private StoryRepository storyRepository;
    @Mock private StoryLikeRepository storyLikeRepository;
    @Mock private StoryVisibilityService storyVisibilityService;

    private StoryLikeServiceImpl service;

    private final UUID userId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID storyId = UUID.randomUUID();
    private final StoryLikeId likeId = new StoryLikeId(userId, storyId);

    @BeforeEach
    void setUp() {
        service =
                new StoryLikeServiceImpl(
                        storyRepository, storyLikeRepository, storyVisibilityService);
    }

    private Story story() {
        return Story.builder()
                .id(storyId)
                .userId(ownerId)
                .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1))
                .build();
    }

    @Test
    void likeStory_missingOrExpired_throwsNotFound() {
        when(storyRepository.findActiveById(eq(storyId), any(OffsetDateTime.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.likeStory(userId, storyId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.STORY_NOT_FOUND);
        verifyNoInteractions(storyLikeRepository);
    }

    @Test
    void likeStory_notVisible_throwsNotFound() {
        Story story = story();
        when(storyRepository.findActiveById(eq(storyId), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(story));
        when(storyVisibilityService.isVisibleTo(userId, story)).thenReturn(false);

        assertThatThrownBy(() -> service.likeStory(userId, storyId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.STORY_NOT_FOUND);
        verifyNoInteractions(storyLikeRepository);
    }

    @Test
    void likeStory_alreadyLiked_throwsStoryAlreadyLiked() {
        Story story = story();
        when(storyRepository.findActiveById(eq(storyId), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(story));
        when(storyVisibilityService.isVisibleTo(userId, story)).thenReturn(true);
        when(storyLikeRepository.existsById(likeId)).thenReturn(true);

        assertThatThrownBy(() -> service.likeStory(userId, storyId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.STORY_ALREADY_LIKED);
        verify(storyLikeRepository, never()).saveAndFlush(any());
    }

    @Test
    void likeStory_firstLike_savesAndReturnsFreshCount() {
        Story story = story();
        when(storyRepository.findActiveById(eq(storyId), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(story));
        when(storyVisibilityService.isVisibleTo(userId, story)).thenReturn(true);
        when(storyLikeRepository.existsById(likeId)).thenReturn(false);
        when(storyLikeRepository.saveAndFlush(any(StoryLike.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(storyRepository.findLikeCount(storyId)).thenReturn(1);

        StoryLikeActionResponse response = service.likeStory(userId, storyId);

        assertThat(response.liked()).isTrue();
        assertThat(response.likeCount()).isEqualTo(1);
    }

    @Test
    void likeStory_selfLike_isPermitted() {
        Story story = story();
        when(storyRepository.findActiveById(eq(storyId), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(story));
        when(storyVisibilityService.isVisibleTo(ownerId, story)).thenReturn(true);
        when(storyLikeRepository.existsById(new StoryLikeId(ownerId, storyId))).thenReturn(false);
        when(storyLikeRepository.saveAndFlush(any(StoryLike.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(storyRepository.findLikeCount(storyId)).thenReturn(1);

        StoryLikeActionResponse response = service.likeStory(ownerId, storyId);

        assertThat(response.liked()).isTrue();
    }

    @Test
    void likeStory_concurrentDuplicateInsert_throwsAlreadyLiked() {
        Story story = story();
        when(storyRepository.findActiveById(eq(storyId), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(story));
        when(storyVisibilityService.isVisibleTo(userId, story)).thenReturn(true);
        when(storyLikeRepository.existsById(likeId)).thenReturn(false);
        when(storyLikeRepository.saveAndFlush(any(StoryLike.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.likeStory(userId, storyId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.STORY_ALREADY_LIKED);
    }

    @Test
    void unlikeStory_missingOrExpired_throwsNotFound() {
        when(storyRepository.findActiveById(eq(storyId), any(OffsetDateTime.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unlikeStory(userId, storyId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.STORY_NOT_FOUND);
        verifyNoInteractions(storyLikeRepository);
    }

    @Test
    void unlikeStory_notLiked_throwsStoryNotFound() {
        Story story = story();
        when(storyRepository.findActiveById(eq(storyId), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(story));
        when(storyVisibilityService.isVisibleTo(userId, story)).thenReturn(true);
        when(storyLikeRepository.deleteByUserAndStory(userId, storyId)).thenReturn(0);

        assertThatThrownBy(() -> service.unlikeStory(userId, storyId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.STORY_NOT_FOUND);
    }

    @Test
    void unlikeStory_liked_deletesAndReturnsFreshCount() {
        Story story = story();
        when(storyRepository.findActiveById(eq(storyId), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(story));
        when(storyVisibilityService.isVisibleTo(userId, story)).thenReturn(true);
        when(storyLikeRepository.deleteByUserAndStory(userId, storyId)).thenReturn(1);
        when(storyRepository.findLikeCount(storyId)).thenReturn(0);

        StoryLikeActionResponse response = service.unlikeStory(userId, storyId);

        assertThat(response.liked()).isFalse();
        assertThat(response.likeCount()).isEqualTo(0);
    }
}
