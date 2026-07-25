package com.app.modules.story.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Collections;
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
import com.app.common.outbox.service.OutboxService;
import com.app.common.response.CursorPageResponse;
import com.app.modules.story.dto.response.StoryViewActionResponse;
import com.app.modules.story.dto.response.StoryViewerResponse;
import com.app.modules.story.entity.Story;
import com.app.modules.story.entity.StoryView;
import com.app.modules.story.mapper.StoryMapper;
import com.app.modules.story.messaging.StoryEventTypes;
import com.app.modules.story.repository.StoryRepository;
import com.app.modules.story.repository.StoryUserRepository;
import com.app.modules.story.repository.StoryViewRepository;
import com.app.modules.story.service.StoryViewService;
import com.app.modules.story.service.StoryVisibilityService;
import com.app.modules.users.entity.User;

@Service
public class StoryViewServiceImpl implements StoryViewService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String AGGREGATE_TYPE = "story";

    private final StoryRepository storyRepository;
    private final StoryViewRepository storyViewRepository;
    private final StoryUserRepository storyUserRepository;
    private final StoryVisibilityService storyVisibilityService;
    private final StoryMapper storyMapper;
    private final OutboxService outboxService;

    public StoryViewServiceImpl(
            StoryRepository storyRepository,
            StoryViewRepository storyViewRepository,
            StoryUserRepository storyUserRepository,
            StoryVisibilityService storyVisibilityService,
            StoryMapper storyMapper,
            OutboxService outboxService) {
        this.storyRepository = storyRepository;
        this.storyViewRepository = storyViewRepository;
        this.storyUserRepository = storyUserRepository;
        this.storyVisibilityService = storyVisibilityService;
        this.storyMapper = storyMapper;
        this.outboxService = outboxService;
    }

    @Override
    @Transactional
    public StoryViewActionResponse recordView(UUID viewerId, UUID storyId) {
        Story story = fetchVisibleActiveStory(viewerId, storyId);
        // Owner views never create a story_views row and are never counted (DATA_RULES §3B).
        if (viewerId.equals(story.getUserId())) {
            return new StoryViewActionResponse(storyId, false, story.getViewCount());
        }
        int rows = storyViewRepository.insertIgnoringDuplicate(storyId, viewerId);
        if (rows == 1) {
            Map<String, Object> data =
                    Map.of(
                            "storyId", storyId.toString(),
                            "ownerId", story.getUserId().toString());
            outboxService.enqueue(
                    StoryEventTypes.STORY_VIEWED_V1,
                    StoryEventTypes.STORY_VIEWED_V1,
                    AGGREGATE_TYPE,
                    storyId,
                    viewerId,
                    data);
        }
        // Re-read past the possibly stale first-level cached entity now that the trigger has run.
        return new StoryViewActionResponse(
                storyId, rows == 1, storyRepository.findViewCount(storyId));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<StoryViewerResponse> listViewers(
            UUID requesterId, UUID storyId, String cursor, int limit) {
        Story story =
                storyRepository
                        .findActiveById(storyId, OffsetDateTime.now(ZoneOffset.UTC))
                        .orElseThrow(() -> new AppException(ApiErrorCode.STORY_NOT_FOUND));
        if (!requesterId.equals(story.getUserId())) {
            throw new AppException(ApiErrorCode.STORY_FORBIDDEN);
        }
        int pageSize = normalizeLimit(limit);
        ViewerCursor decoded = decodeCursor(cursor);
        PageRequest page = PageRequest.of(0, pageSize + 1);
        List<StoryView> views =
                decoded.isEmpty()
                        ? storyViewRepository.findFirstViewers(storyId, page)
                        : storyViewRepository.findViewersBefore(
                                storyId, decoded.viewedAt(), decoded.viewerId(), page);
        boolean hasNextPage = views.size() > pageSize;
        if (hasNextPage) {
            views = views.subList(0, pageSize);
        }
        if (views.isEmpty()) {
            return CursorPageResponse.of(
                    Collections.emptyList(), false, null, null, cursor != null);
        }
        List<UUID> viewerIds = views.stream().map(v -> v.getId().getViewerId()).toList();
        Map<UUID, User> users =
                storyUserRepository.findAllByIdInAndDeletedAtIsNull(viewerIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        List<StoryViewerResponse> content =
                views.stream()
                        .filter(v -> users.containsKey(v.getId().getViewerId()))
                        .map(
                                v ->
                                        storyMapper.toViewerResponse(
                                                users.get(v.getId().getViewerId()),
                                                v.getViewedAt()))
                        .toList();
        String startCursor = encodeCursor(views.get(0));
        String endCursor = encodeCursor(views.get(views.size() - 1));
        return CursorPageResponse.of(content, hasNextPage, startCursor, endCursor, cursor != null);
    }

    private Story fetchVisibleActiveStory(UUID viewerId, UUID storyId) {
        Story story =
                storyRepository
                        .findActiveById(storyId, OffsetDateTime.now(ZoneOffset.UTC))
                        .orElseThrow(() -> new AppException(ApiErrorCode.STORY_NOT_FOUND));
        // Visibility rejections mask as not-found on this write path, mirroring the post-like gate.
        if (!storyVisibilityService.isVisibleTo(viewerId, story)) {
            throw new AppException(ApiErrorCode.STORY_NOT_FOUND);
        }
        return story;
    }

    private int normalizeLimit(int limit) {
        return limit > MAX_PAGE_SIZE ? MAX_PAGE_SIZE : (limit < 1 ? DEFAULT_PAGE_SIZE : limit);
    }

    private String encodeCursor(StoryView view) {
        String raw = view.getViewedAt() + "|" + view.getId().getViewerId();
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private ViewerCursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new ViewerCursor(null, null);
        }
        try {
            String raw = new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Cursor must contain viewedAt and viewerId");
            }
            return new ViewerCursor(OffsetDateTime.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException e) {
            throw new AppException(ApiErrorCode.BAD_REQUEST, "Invalid cursor format");
        }
    }

    private record ViewerCursor(OffsetDateTime viewedAt, UUID viewerId) {
        boolean isEmpty() {
            return viewedAt == null || viewerId == null;
        }
    }
}
