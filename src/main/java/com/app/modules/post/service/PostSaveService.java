package com.app.modules.post.service;

import java.util.UUID;

import com.app.common.response.CursorPageResponse;
import com.app.modules.post.dto.response.SavedPostResponse;

/** Domain API for post save (bookmark) actions and the saved-posts list. */
public interface PostSaveService {

    /**
     * Saves a post for the user.
     *
     * <p>The post must be published and visible to the user. Saving an already-saved post is
     * rejected with a conflict. {@code posts.save_count} is maintained by the database trigger.
     *
     * @param userId authenticated user
     * @param postId post to save
     */
    void savePost(UUID userId, UUID postId);

    /**
     * Removes the user's save from a post.
     *
     * <p>Unsaving a post that is not saved is rejected as not found. Visibility is not re-checked:
     * a user may always withdraw their own bookmark.
     *
     * @param userId authenticated user
     * @param postId post to unsave
     */
    void unsavePost(UUID userId, UUID postId);

    /**
     * Cursor-paginated posts the user has saved, newest save first.
     *
     * <p>Saved posts that have since been soft-deleted, unpublished, or hidden by account-level
     * visibility (blocks, private accounts without an accepted follow) are filtered out; a page may
     * therefore contain fewer items than requested.
     *
     * @param userId authenticated user listing their own saves
     * @param cursor opaque base64 cursor from the previous page; null or blank for the first page
     * @param size requested page size, normalized to 1–100 with a default of 20
     * @return cursor page of saved posts with save timestamps
     */
    CursorPageResponse<SavedPostResponse> listSavedPosts(UUID userId, String cursor, int size);
}
