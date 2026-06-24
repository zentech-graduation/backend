package com.app.modules.comment.service;

import java.util.List;
import java.util.UUID;

import com.app.modules.comment.dto.response.CommentResponse;

/**
 * Recent top-level comments cache for a post.
 *
 * <p>Backed by a capped Redis list with a short TTL. All operations fail open: on a Redis error the
 * cache degrades to empty or a no-op and never propagates the failure, since it is rebuildable from
 * PostgreSQL.
 */
public interface CommentCacheService {

    /**
     * Returns cached recent top-level comments, newest first; empty on a miss or Redis error.
     *
     * @param postId post whose recent comments are read
     * @return cached comments, possibly empty
     */
    List<CommentResponse> getRecent(UUID postId);

    /**
     * Returns cached recent comments, rebuilding from PostgreSQL on a miss.
     *
     * @param postId post whose recent comments are read
     * @return recent comments, newest first
     */
    List<CommentResponse> getOrRebuild(UUID postId);

    /**
     * Prepends a newly created comment to the cache, capping and refreshing the TTL.
     *
     * @param postId post the comment belongs to
     * @param comment the created comment
     */
    void pushToFront(UUID postId, CommentResponse comment);

    /**
     * Drops the cached list for a post so the next read rebuilds from PostgreSQL.
     *
     * @param postId post whose cache is invalidated
     */
    void invalidate(UUID postId);
}
