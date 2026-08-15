package com.app.modules.post.service;

import java.util.Set;
import java.util.UUID;

/**
 * The requesting viewer's like, save, and report membership over a batch of posts.
 *
 * <p>Returned by {@link PostViewerStateService#load}; a post id absent from a set means the viewer
 * has not taken that action on that post.
 */
public record PostViewerState(
        Set<UUID> likedPostIds, Set<UUID> savedPostIds, Set<UUID> reportedPostIds) {

    /** Shared empty instance for an anonymous viewer or an empty post batch. */
    public static final PostViewerState NONE = new PostViewerState(Set.of(), Set.of(), Set.of());

    public boolean isLiked(UUID postId) {
        return likedPostIds.contains(postId);
    }

    public boolean isSaved(UUID postId) {
        return savedPostIds.contains(postId);
    }

    public boolean hasReported(UUID postId) {
        return reportedPostIds.contains(postId);
    }
}
