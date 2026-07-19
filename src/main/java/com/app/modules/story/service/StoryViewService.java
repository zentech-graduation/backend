package com.app.modules.story.service;

import java.util.UUID;

import com.app.common.response.CursorPageResponse;
import com.app.modules.story.dto.response.StoryViewActionResponse;
import com.app.modules.story.dto.response.StoryViewerResponse;

/** Deduplicated story view recording and the owner-only viewer list. */
public interface StoryViewService {

    /**
     * Records a view of the story for the viewer, deduplicated by {@code (story_id, viewer_id)}.
     *
     * <p>Owner views never insert a row and are not counted; repeat views by the same non-owner
     * viewer are idempotent no-ops. The trigger-maintained view count is always fresh in the
     * response.
     *
     * @param viewerId the authenticated user recording the view
     * @param storyId the story being viewed
     * @return whether this call inserted a new view row, plus the current view count
     * @throws com.app.common.exception.AppException STORY_NOT_FOUND when missing, expired, or not
     *     visible to the viewer
     */
    StoryViewActionResponse recordView(UUID viewerId, UUID storyId);

    /**
     * Lists the story's viewers, newest view first. Owner-only.
     *
     * @param requesterId the authenticated user
     * @param storyId the story to inspect
     * @param cursor opaque continuation cursor from a previous page, or null for the first page
     * @param limit requested page size, clamped between 1 and 100
     * @return a cursor page of viewers
     * @throws com.app.common.exception.AppException STORY_NOT_FOUND when missing or expired;
     *     STORY_FORBIDDEN when the requester is not the owner; BAD_REQUEST on a malformed cursor
     */
    CursorPageResponse<StoryViewerResponse> listViewers(
            UUID requesterId, UUID storyId, String cursor, int limit);
}
