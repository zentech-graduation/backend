package com.app.modules.story.service;

import java.util.UUID;

import com.app.modules.story.entity.Story;

/** Visibility rules for stories against the follow graph and block list. */
public interface StoryVisibilityService {

    /**
     * Decides whether the viewer may see the story.
     *
     * <p>Owner always sees their own story; a block in either direction hides it; a soft-deleted
     * owner hides all of their stories; a private owner requires an accepted follow.
     *
     * @param viewerId the requesting user
     * @param story the story to check
     * @return true when the story is visible to the viewer
     */
    boolean isVisibleTo(UUID viewerId, Story story);
}
