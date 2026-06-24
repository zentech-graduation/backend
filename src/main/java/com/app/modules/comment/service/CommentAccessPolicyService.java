package com.app.modules.comment.service;

import java.util.UUID;

import com.app.modules.post.entity.Post;

/** Authorization decisions for commenting on a post. */
public interface CommentAccessPolicyService {

    /**
     * Asserts that the viewer may comment on the given post.
     *
     * <p>Delegates to post visibility, which already enforces block relationships and private
     * follow gating. Throws {@code AppException} with {@code POST_COMMENTING_RESTRICTED} when the
     * post is not visible to the viewer.
     *
     * @param viewerId authenticated user attempting to comment
     * @param post target post
     */
    void assertCanComment(UUID viewerId, Post post);
}
