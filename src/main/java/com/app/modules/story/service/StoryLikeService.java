package com.app.modules.story.service;

import java.util.UUID;

import com.app.modules.story.dto.response.StoryLikeActionResponse;

/** Like/unlike actions on a story. */
public interface StoryLikeService {

    /**
     * Likes an active story visible to the caller. Self-like is permitted. Liking an already-liked
     * story throws a conflict.
     *
     * @param userId the authenticated caller
     * @param storyId the story to like
     * @return the resulting like state and fresh count
     */
    StoryLikeActionResponse likeStory(UUID userId, UUID storyId);

    /**
     * Removes the caller's like from a story. Unliking a story that is not liked throws not-found.
     *
     * @param userId the authenticated caller
     * @param storyId the story to unlike
     * @return the resulting like state and fresh count
     */
    StoryLikeActionResponse unlikeStory(UUID userId, UUID storyId);
}
