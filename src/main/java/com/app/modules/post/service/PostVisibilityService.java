package com.app.modules.post.service;

import java.util.UUID;

import com.app.modules.post.entity.Post;

/** Account-level visibility decisions for posts. */
public interface PostVisibilityService {

    /**
     * Decides whether the viewer may see the given post based on account-level state.
     *
     * <p>The owner always sees their own posts. A block in either direction hides the post. A
     * private owner account requires the viewer to hold an accepted follow. A soft-deleted owner
     * hides all of their content. Post status gating (draft, archived) is the caller's concern.
     *
     * @param viewerId authenticated viewer
     * @param post the post whose owner's account state is evaluated
     * @return true when the post is visible to the viewer
     */
    boolean isVisibleTo(UUID viewerId, Post post);
}
