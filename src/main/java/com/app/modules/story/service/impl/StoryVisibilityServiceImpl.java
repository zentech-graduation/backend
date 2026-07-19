package com.app.modules.story.service.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.modules.social.service.SocialService;
import com.app.modules.story.entity.Story;
import com.app.modules.story.repository.StoryUserRepository;
import com.app.modules.story.service.StoryVisibilityService;
import com.app.modules.users.entity.User;

@Service
public class StoryVisibilityServiceImpl implements StoryVisibilityService {

    private final StoryUserRepository storyUserRepository;
    private final SocialService socialService;

    public StoryVisibilityServiceImpl(
            StoryUserRepository storyUserRepository, SocialService socialService) {
        this.storyUserRepository = storyUserRepository;
        this.socialService = socialService;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isVisibleTo(UUID viewerId, Story story) {
        UUID ownerId = story.getUserId();
        if (viewerId.equals(ownerId)) {
            return true;
        }
        if (socialService.isBlockedBetween(viewerId, ownerId)) {
            return false;
        }
        // A soft-deleted owner hides all of their content even though the story rows remain live.
        User owner = storyUserRepository.findByIdAndDeletedAtIsNull(ownerId).orElse(null);
        if (owner == null) {
            return false;
        }
        if (owner.isPrivate()) {
            return socialService.hasAcceptedFollow(viewerId, ownerId);
        }
        return true;
    }
}
