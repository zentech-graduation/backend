package com.app.modules.post.service.impl;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.response.ViewerRelationshipResponse;
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

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> filterVisibleOwnerIds(UUID viewerId, Collection<UUID> ownerIds) {
        if (ownerIds.isEmpty()) {
            return Set.of();
        }
        // Two fixed-count batched round trips (relationships, owners) replace what would otherwise
        // be up to three queries per candidate owner if isVisibleTo were called in a loop.
        Map<UUID, ViewerRelationshipResponse> relationships =
                socialService.loadRelationships(viewerId, ownerIds);
        List<User> owners = postUserRepository.findAllByIdInAndDeletedAtIsNull(ownerIds);
        Map<UUID, User> ownersById =
                owners.stream().collect(Collectors.toMap(User::getId, Function.identity()));

        Set<UUID> visible = new HashSet<>();
        for (UUID ownerId : ownerIds) {
            if (viewerId.equals(ownerId)) {
                visible.add(ownerId);
                continue;
            }
            ViewerRelationshipResponse relationship =
                    relationships.getOrDefault(ownerId, ViewerRelationshipResponse.NONE);
            if (relationship.isBlocking() || relationship.isBlockedBy()) {
                continue;
            }
            // A soft-deleted owner is absent from ownersById and hides all of their content.
            User owner = ownersById.get(ownerId);
            if (owner == null) {
                continue;
            }
            if (owner.isPrivate() && !relationship.isFollowing()) {
                continue;
            }
            visible.add(ownerId);
        }
        return visible;
    }
}
