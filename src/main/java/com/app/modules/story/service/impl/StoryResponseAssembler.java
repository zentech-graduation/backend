package com.app.modules.story.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.app.modules.media.entity.MediaAsset;
import com.app.modules.story.dto.response.StoryResponse;
import com.app.modules.story.entity.Story;
import com.app.modules.story.mapper.StoryMapper;
import com.app.modules.story.repository.StoryMediaAssetRepository;
import com.app.modules.story.repository.StoryUserRepository;
import com.app.modules.users.entity.User;

/**
 * Assembles {@link StoryResponse} DTOs by joining stories with their {@code media_assets} row and
 * owner {@code users} row.
 *
 * <p>Shared by the lifecycle and feed paths so media and author hydration stay in one place. The
 * viewer id decides the context fields: owners get {@code viewCount} and no {@code seen} flag,
 * other viewers get {@code seen} and no {@code viewCount}.
 */
@Component
public class StoryResponseAssembler {

    private final StoryMediaAssetRepository storyMediaAssetRepository;
    private final StoryUserRepository storyUserRepository;
    private final StoryMapper storyMapper;

    public StoryResponseAssembler(
            StoryMediaAssetRepository storyMediaAssetRepository,
            StoryUserRepository storyUserRepository,
            StoryMapper storyMapper) {
        this.storyMediaAssetRepository = storyMediaAssetRepository;
        this.storyUserRepository = storyUserRepository;
        this.storyMapper = storyMapper;
    }

    public StoryResponse assemble(UUID viewerId, Story story, Set<UUID> seenStoryIds) {
        return assemble(viewerId, List.of(story), seenStoryIds).get(0);
    }

    public List<StoryResponse> assemble(
            UUID viewerId, List<Story> stories, Set<UUID> seenStoryIds) {
        // Single batched asset lookup avoids one media_assets query per story on list pages.
        Set<UUID> assetIds =
                stories.stream().map(Story::getMediaAssetId).collect(Collectors.toSet());
        Map<UUID, MediaAsset> assets =
                assetIds.isEmpty()
                        ? Map.of()
                        : storyMediaAssetRepository.findAllById(assetIds).stream()
                                .collect(Collectors.toMap(MediaAsset::getId, a -> a));
        Set<UUID> ownerIds = stories.stream().map(Story::getUserId).collect(Collectors.toSet());
        Map<UUID, User> owners =
                ownerIds.isEmpty()
                        ? Map.of()
                        : storyUserRepository.findAllByIdInAndDeletedAtIsNull(ownerIds).stream()
                                .collect(Collectors.toMap(User::getId, u -> u));
        return stories.stream()
                .map(
                        story -> {
                            boolean isOwner = viewerId.equals(story.getUserId());
                            MediaAsset asset = assets.get(story.getMediaAssetId());
                            return storyMapper.toResponse(
                                    story,
                                    asset == null ? null : storyMapper.toMediaResponse(asset),
                                    owners.get(story.getUserId()),
                                    isOwner ? story.getViewCount() : null,
                                    isOwner ? null : seenStoryIds.contains(story.getId()));
                        })
                .toList();
    }
}
