package com.app.modules.comment.service;

import java.util.UUID;

import com.app.common.response.CursorPageResponse;
import com.app.modules.comment.dto.request.CreateCommentRequest;
import com.app.modules.comment.dto.request.EditCommentRequest;
import com.app.modules.comment.dto.response.CommentResponse;

/** Comment creation, editing, soft deletion, likes, and threaded listing. */
public interface CommentService {

    /**
     * Creates a comment or reply after access, depth, moderation, slow-mode, and idempotency
     * checks, and enqueues a {@code comment.created.v1} outbox event in the same transaction.
     *
     * <p>When {@code idempotencyKey} is non-null, a retry with the same key and identical payload
     * replays the original response; the same key with a different payload is a conflict.
     * Notifications are produced asynchronously by the comment consumer, never synchronously here.
     *
     * @param actorId authenticated author
     * @param request post id, optional parent id, and content
     * @param idempotencyKey optional client-supplied idempotency key
     * @return the created comment
     */
    CommentResponse createComment(
            UUID actorId, CreateCommentRequest request, String idempotencyKey);

    /**
     * Edits the body of a comment owned by the actor and enqueues a {@code comment.edited.v1}
     * event.
     *
     * @param actorId authenticated author; must own the comment
     * @param commentId comment to edit
     * @param request new content
     * @return the updated comment
     */
    CommentResponse editComment(UUID actorId, UUID commentId, EditCommentRequest request);

    /**
     * Soft-deletes a comment and its entire subtree. Allowed for the comment owner or an admin.
     * Enqueues a {@code comment.deleted.v1} event.
     *
     * @param actorId authenticated user; must own the comment or be an admin
     * @param commentId root of the subtree to delete
     */
    void deleteComment(UUID actorId, UUID commentId);

    /**
     * Likes a comment. Self-likes are rejected; a duplicate like is a conflict. Enqueues a {@code
     * comment.liked.v1} event.
     *
     * @param actorId authenticated user
     * @param commentId comment to like
     */
    void likeComment(UUID actorId, UUID commentId);

    /**
     * Removes the actor's like from a comment. A no-op unlike is a conflict. Enqueues a {@code
     * comment.unliked.v1} event.
     *
     * @param actorId authenticated user
     * @param commentId comment to unlike
     */
    void unlikeComment(UUID actorId, UUID commentId);

    /**
     * Lists approved top-level comments for a post, newest first, using keyset pagination.
     *
     * <p>The first page only is preceded by up to three pinned top comments ranked by like count,
     * marked with {@code pinned} and additional to {@code limit}, and excluded from that page's
     * newest-first body so neither is returned twice. Page two onward is the pure keyset stream.
     *
     * @param viewerId authenticated viewer, or null for anonymous access
     * @param postId post whose comments are listed
     * @param cursor opaque cursor from a previous page; null for the first page
     * @param limit maximum number of comments to return in the newest-first body, excluding the
     *     pinned block
     * @return a cursor page of top-level comments
     */
    CursorPageResponse<CommentResponse> listTopLevelComments(
            UUID viewerId, UUID postId, String cursor, int limit);

    /**
     * Lists approved direct replies to a comment, newest first, using keyset pagination.
     *
     * @param viewerId authenticated viewer, or null for anonymous access
     * @param commentId parent comment whose direct replies are listed
     * @param cursor opaque cursor from a previous page; null for the first page
     * @param limit maximum number of replies to return
     * @return a cursor page of replies
     */
    CursorPageResponse<CommentResponse> listReplies(
            UUID viewerId, UUID commentId, String cursor, int limit);
}
