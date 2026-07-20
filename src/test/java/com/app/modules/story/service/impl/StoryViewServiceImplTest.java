package com.app.modules.story.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.outbox.service.OutboxService;
import com.app.common.response.CursorPageResponse;
import com.app.modules.story.dto.response.StoryViewActionResponse;
import com.app.modules.story.dto.response.StoryViewerResponse;
import com.app.modules.story.entity.Story;
import com.app.modules.story.entity.StoryView;
import com.app.modules.story.entity.StoryViewId;
import com.app.modules.story.mapper.StoryMapper;
import com.app.modules.story.messaging.StoryEventTypes;
import com.app.modules.story.repository.StoryRepository;
import com.app.modules.story.repository.StoryUserRepository;
import com.app.modules.story.repository.StoryViewRepository;
import com.app.modules.story.service.StoryVisibilityService;
import com.app.modules.users.entity.User;

@ExtendWith(MockitoExtension.class)
class StoryViewServiceImplTest {

    @Mock private StoryRepository storyRepository;
    @Mock private StoryViewRepository storyViewRepository;
    @Mock private StoryUserRepository storyUserRepository;
    @Mock private StoryVisibilityService storyVisibilityService;
    @Mock private StoryMapper storyMapper;
    @Mock private OutboxService outboxService;

    private StoryViewServiceImpl service;

    private final UUID viewerId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID storyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new StoryViewServiceImpl(
                        storyRepository,
                        storyViewRepository,
                        storyUserRepository,
                        storyVisibilityService,
                        storyMapper,
                        outboxService);
    }

    private Story story(UUID id, UUID userId, int viewCount) {
        return Story.builder()
                .id(id)
                .userId(userId)
                .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1))
                .viewCount(viewCount)
                .build();
    }

    @Test
    void recordView_missingOrExpired_throwsNotFound() {
        when(storyRepository.findActiveById(eq(storyId), any(OffsetDateTime.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordView(viewerId, storyId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.STORY_NOT_FOUND);
        verifyNoInteractions(storyViewRepository, outboxService);
    }

    @Test
    void recordView_notVisible_throwsNotFound() {
        Story story = story(storyId, ownerId, 0);
        when(storyRepository.findActiveById(eq(storyId), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(story));
        when(storyVisibilityService.isVisibleTo(viewerId, story)).thenReturn(false);

        assertThatThrownBy(() -> service.recordView(viewerId, storyId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.STORY_NOT_FOUND);
        verifyNoInteractions(storyViewRepository, outboxService);
    }

    @Test
    void recordView_owner_doesNotInsertOrEnqueue() {
        Story story = story(storyId, ownerId, 3);
        when(storyRepository.findActiveById(eq(storyId), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(story));
        when(storyVisibilityService.isVisibleTo(ownerId, story)).thenReturn(true);

        StoryViewActionResponse response = service.recordView(ownerId, storyId);

        assertThat(response.viewed()).isFalse();
        assertThat(response.viewCount()).isEqualTo(3);
        verifyNoInteractions(storyViewRepository, outboxService);
    }

    @Test
    void recordView_firstView_insertsAndEnqueuesViewedEvent() {
        Story story = story(storyId, ownerId, 0);
        when(storyRepository.findActiveById(eq(storyId), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(story));
        when(storyVisibilityService.isVisibleTo(viewerId, story)).thenReturn(true);
        when(storyViewRepository.insertIgnoringDuplicate(storyId, viewerId)).thenReturn(1);
        when(storyRepository.findViewCount(storyId)).thenReturn(1);

        StoryViewActionResponse response = service.recordView(viewerId, storyId);

        assertThat(response.viewed()).isTrue();
        assertThat(response.viewCount()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(outboxService)
                .enqueue(
                        eq(StoryEventTypes.STORY_VIEWED_V1),
                        eq(StoryEventTypes.STORY_VIEWED_V1),
                        eq("story"),
                        eq(storyId),
                        eq(viewerId),
                        dataCaptor.capture());
        assertThat(dataCaptor.getValue())
                .containsEntry("storyId", storyId.toString())
                .containsEntry("ownerId", ownerId.toString());
    }

    @Test
    void recordView_duplicate_returnsOkWithoutEvent() {
        Story story = story(storyId, ownerId, 1);
        when(storyRepository.findActiveById(eq(storyId), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(story));
        when(storyVisibilityService.isVisibleTo(viewerId, story)).thenReturn(true);
        when(storyViewRepository.insertIgnoringDuplicate(storyId, viewerId)).thenReturn(0);
        when(storyRepository.findViewCount(storyId)).thenReturn(1);

        StoryViewActionResponse response = service.recordView(viewerId, storyId);

        assertThat(response.viewed()).isFalse();
        assertThat(response.viewCount()).isEqualTo(1);
        verifyNoInteractions(outboxService);
    }

    @Test
    void listViewers_missingOrExpired_throwsNotFound() {
        when(storyRepository.findActiveById(eq(storyId), any(OffsetDateTime.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listViewers(ownerId, storyId, null, 20))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.STORY_NOT_FOUND);
    }

    @Test
    void listViewers_nonOwner_throwsForbidden() {
        Story story = story(storyId, ownerId, 0);
        when(storyRepository.findActiveById(eq(storyId), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(story));

        assertThatThrownBy(() -> service.listViewers(viewerId, storyId, null, 20))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.STORY_FORBIDDEN);
        verifyNoInteractions(storyViewRepository);
    }

    @Test
    void listViewers_invalidCursor_throwsBadRequest() {
        Story story = story(storyId, ownerId, 0);
        when(storyRepository.findActiveById(eq(storyId), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(story));

        assertThatThrownBy(() -> service.listViewers(ownerId, storyId, "not-base64!!", 20))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.BAD_REQUEST);
    }

    @Test
    void listViewers_overLimit_clampsTo100() {
        Story story = story(storyId, ownerId, 0);
        when(storyRepository.findActiveById(eq(storyId), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(story));
        when(storyViewRepository.findFirstViewers(eq(storyId), any())).thenReturn(List.of());

        service.listViewers(ownerId, storyId, null, 500);

        ArgumentCaptor<org.springframework.data.domain.PageRequest> pageCaptor =
                ArgumentCaptor.forClass(org.springframework.data.domain.PageRequest.class);
        verify(storyViewRepository).findFirstViewers(eq(storyId), pageCaptor.capture());
        assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(101);
    }

    @Test
    void listViewers_firstPage_returnsHydratedViewers() {
        Story story = story(storyId, ownerId, 1);
        OffsetDateTime viewedAt = OffsetDateTime.now(ZoneOffset.UTC);
        StoryView view =
                StoryView.builder()
                        .id(new StoryViewId(storyId, viewerId))
                        .viewedAt(viewedAt)
                        .build();
        User user = User.builder().id(viewerId).username("viewer").build();
        StoryViewerResponse expected =
                new StoryViewerResponse(viewerId, "viewer", null, null, viewedAt);

        when(storyRepository.findActiveById(eq(storyId), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(story));
        when(storyViewRepository.findFirstViewers(eq(storyId), any())).thenReturn(List.of(view));
        when(storyUserRepository.findAllByIdInAndDeletedAtIsNull(anyList()))
                .thenReturn(List.of(user));
        when(storyMapper.toViewerResponse(user, viewedAt)).thenReturn(expected);

        CursorPageResponse<StoryViewerResponse> page =
                service.listViewers(ownerId, storyId, null, 20);

        assertThat(page.getContent()).containsExactly(expected);
        assertThat(page.getPageInfo().isHasPreviousPage()).isFalse();
    }

    @Test
    void listViewers_validCursor_queriesBeforeCursor() {
        Story story = story(storyId, ownerId, 0);
        OffsetDateTime cursorTime = OffsetDateTime.now(ZoneOffset.UTC);
        UUID cursorViewerId = UUID.randomUUID();
        String cursor =
                Base64.getEncoder().encodeToString((cursorTime + "|" + cursorViewerId).getBytes());
        when(storyRepository.findActiveById(eq(storyId), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(story));
        when(storyViewRepository.findViewersBefore(
                        eq(storyId), eq(cursorTime), eq(cursorViewerId), any()))
                .thenReturn(List.of());

        service.listViewers(ownerId, storyId, cursor, 20);

        verify(storyViewRepository)
                .findViewersBefore(eq(storyId), eq(cursorTime), eq(cursorViewerId), any());
        verify(storyViewRepository, never()).findFirstViewers(any(), any());
    }
}
