package com.app.modules.story.service.impl;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.story.dto.response.StoryLikeActionResponse;
import com.app.modules.story.entity.Story;
import com.app.modules.story.entity.StoryLike;
import com.app.modules.story.entity.StoryLikeId;
import com.app.modules.story.repository.StoryLikeRepository;
import com.app.modules.story.repository.StoryRepository;
import com.app.modules.story.service.StoryLikeService;
import com.app.modules.story.service.StoryVisibilityService;

@Service
public class StoryLikeServiceImpl implements StoryLikeService {

    private final StoryRepository storyRepository;
    private final StoryLikeRepository storyLikeRepository;
    private final StoryVisibilityService storyVisibilityService;

    public StoryLikeServiceImpl(
            StoryRepository storyRepository,
            StoryLikeRepository storyLikeRepository,
            StoryVisibilityService storyVisibilityService) {
        this.storyRepository = storyRepository;
        this.storyLikeRepository = storyLikeRepository;
        this.storyVisibilityService = storyVisibilityService;
    }

    @Override
    @Transactional
    public StoryLikeActionResponse likeStory(UUID userId, UUID storyId) {
        fetchVisibleActiveStory(userId, storyId);
        StoryLikeId likeId = new StoryLikeId(userId, storyId);
        if (storyLikeRepository.existsById(likeId)) {
            throw new AppException(ApiErrorCode.STORY_ALREADY_LIKED);
        }
        // Flush forces the INSERT (and its AFTER INSERT counter trigger) before the scalar
        // re-read; the entity in the persistence context still carries the stale counter.
        try {
            storyLikeRepository.saveAndFlush(StoryLike.builder().id(likeId).build());
        } catch (DataIntegrityViolationException ex) {
            // A concurrent double-submit lost the insert race; the (user_id, story_id) primary
            // key already recorded the like, so surface the same clean conflict rather than 500.
            throw new AppException(ApiErrorCode.STORY_ALREADY_LIKED);
        }
        return new StoryLikeActionResponse(storyId, true, storyRepository.findLikeCount(storyId));
    }

    @Override
    @Transactional
    public StoryLikeActionResponse unlikeStory(UUID userId, UUID storyId) {
        // Visibility is checked before the delete so a hidden story cannot be probed for its
        // like state via the outcome of an unlike call.
        fetchVisibleActiveStory(userId, storyId);
        int deleted = storyLikeRepository.deleteByUserAndStory(userId, storyId);
        if (deleted == 0) {
            throw new AppException(ApiErrorCode.STORY_NOT_FOUND);
        }
        return new StoryLikeActionResponse(storyId, false, storyRepository.findLikeCount(storyId));
    }

    private Story fetchVisibleActiveStory(UUID viewerId, UUID storyId) {
        Story story =
                storyRepository
                        .findActiveById(storyId, OffsetDateTime.now(ZoneOffset.UTC))
                        .orElseThrow(() -> new AppException(ApiErrorCode.STORY_NOT_FOUND));
        // Visibility rejections mask as not-found on this write path, mirroring the post-like
        // gate and StoryViewServiceImpl.recordView.
        if (!storyVisibilityService.isVisibleTo(viewerId, story)) {
            throw new AppException(ApiErrorCode.STORY_NOT_FOUND);
        }
        return story;
    }
}
