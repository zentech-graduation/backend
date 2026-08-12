package com.app.modules.post.service;

import java.util.UUID;

import com.app.common.response.CursorPageResponse;
import com.app.common.response.UserListItemResponse;
import com.app.modules.post.dto.response.LikeActionResponse;
import com.app.modules.post.dto.response.LikedPostResponse;

/** Domain API for post like actions and liker listings. */
public interface PostLikeService {

    /**
     * Likes a post on behalf of the user.
     *
     * <p>The post must be published and visible to the user. Liking an already-liked post is
     * rejected with a conflict. Self-like is permitted. {@code posts.like_count} is maintained by
     * the database trigger; the returned count is re-read after the insert.
     *
     * @param userId authenticated user
     * @param postId post to like
     * @return action result with the fresh like count
     */
    LikeActionResponse likePost(UUID userId, UUID postId);

    /**
     * Removes the user's like from a post.
     *
     * <p>Unliking a post that is not liked is rejected as not found. Visibility is not re-checked:
     * a user may always withdraw their own engagement.
     *
     * @param userId authenticated user
     * @param postId post to unlike
     * @return action result with the fresh like count
     */
    LikeActionResponse unlikePost(UUID userId, UUID postId);

    /**
     * Cursor-paginated users who liked the post, newest like first.
     *
     * <p>The post must be published and visible to the viewer. Likers whose accounts are
     * soft-deleted are omitted.
     *
     * @param viewerId authenticated viewer
     * @param postId liked post
     * @param cursor opaque base64 cursor from the previous page; null or blank for the first page
     * @param size requested page size, normalized to 1–100 with a default of 20
     * @return cursor page of liker summaries paired with the viewer's relationship to each liker
     */
    CursorPageResponse<UserListItemResponse> listLikers(
            UUID viewerId, UUID postId, String cursor, int size);

    /**
     * Cursor-paginated posts the authenticated user has liked, newest like first.
     *
     * <p>Self-only: the subject is the caller, so there is no target user and no target-user
     * visibility rule. Posts that are soft-deleted, no longer published, or no longer visible to
     * the caller are omitted, which can return fewer entries than requested.
     *
     * @param userId authenticated user whose likes are listed
     * @param cursor opaque base64 cursor from the previous page; null or blank for the first page
     * @param size requested page size, normalized to 1–100 with a default of 20
     * @return cursor page of liked posts paired with the timestamp of each like
     */
    CursorPageResponse<LikedPostResponse> listLikedPosts(UUID userId, String cursor, int size);
}
