package com.app.modules.post.service.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.modules.post.entity.Post;
import com.app.modules.post.repository.PostUserRepository;
import com.app.modules.post.service.PostVisibilityService;
import com.app.modules.social.service.SocialService;
import com.app.modules.users.entity.User;

@Service
public class PostVisibilityServiceImpl implements PostVisibilityService {

    private final PostUserRepository postUserRepository;
    private final SocialService socialService;

    public PostVisibilityServiceImpl(
            PostUserRepository postUserRepository, SocialService socialService) {
        this.postUserRepository = postUserRepository;
        this.socialService = socialService;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isVisibleTo(UUID viewerId, Post post) {
        UUID ownerId = post.getUserId();
        if (viewerId.equals(ownerId)) {
            return true;
        }
        if (socialService.isBlockedBetween(viewerId, ownerId)) {
            return false;
        }
        // A soft-deleted owner hides all of their content even though the post rows remain live.
        User owner = postUserRepository.findByIdAndDeletedAtIsNull(ownerId).orElse(null);
        if (owner == null) {
            return false;
        }
        if (owner.isPrivate()) {
            return socialService.hasAcceptedFollow(viewerId, ownerId);
        }
        return true;
    }
}
