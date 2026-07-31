package com.app.modules.comment.service;

import java.util.List;
import java.util.UUID;

import com.app.modules.comment.dto.response.CommentBroadcastResponse;
import com.app.modules.comment.dto.response.CommentResponse;

/**
 * Recent top-level comments cache for a post.
 *
 * <p>Backed by a capped Redis list with a short TTL. All operations fail open: on a Redis error the
 * cache degrades to empty or a no-op and never propagates the failure, since it is rebuildable from
 * PostgreSQL. Cached and returned as {@link CommentBroadcastResponse}, not {@link CommentResponse}:
 * this cache backs the catch-up replay broadcast to every subscriber of a post's live stream, and
 * {@code isLiked} cannot be resolved for a blob with no single viewer.
 */
public interface CommentCacheService {

    /**
     * Returns cached recent top-level comments, newest first; empty on a miss or Redis error.
     *
     * @param postId post whose recent comments are read
     * @return cached comments, possibly empty
     */
    List<CommentBroadcastResponse> getRecent(UUID postId);

    /**
     * Returns cached recent comments, rebuilding from PostgreSQL on a miss.
     *
     * @param postId post whose recent comments are read
     * @return recent comments, newest first
     */
    List<CommentBroadcastResponse> getOrRebuild(UUID postId);

    /**
     * Prepends a newly created comment to the cache, capping and refreshing the TTL. The viewer's
     * {@code isLiked} state is stripped before caching.
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
