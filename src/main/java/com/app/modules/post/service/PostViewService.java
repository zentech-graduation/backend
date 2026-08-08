package com.app.modules.post.service;

import java.util.UUID;

import com.app.modules.post.dto.response.PostViewResponse;

/** Domain API for recording that a viewer has seen a post. */
public interface PostViewService {

    /**
     * Records that the viewer has seen a published, visible post.
     *
     * <p>Fire-and-forget from the caller's perspective: the view is recorded as a behavioral event
     * and enqueued for asynchronous processing, never counted synchronously. {@code
     * posts.view_count} is not written here — it is updated later, if at all, by a background job
     * that aggregates the underlying event; see {@code docs/modules/post/DATA_RULES.md}. A view by
     * the post owner on their own post is accepted but not recorded, so self-views can never
     * inflate any downstream signal derived from this event.
     *
     * @param viewerId authenticated viewer
     * @param postId post identifier
     * @return whether the view was recorded
     */
    PostViewResponse recordView(UUID viewerId, UUID postId);
}
