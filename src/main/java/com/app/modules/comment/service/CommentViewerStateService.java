package com.app.modules.comment.service;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves the requesting viewer's like membership over a page of comments in one batched query.
 *
 * <p>The single point every comment response uses to embed {@code isLiked}, so a list endpoint pays
 * a constant, page-size-independent query cost instead of one probe per row.
 */
public interface CommentViewerStateService {

    /**
     * Loads the comment ids among {@code commentIds} that the viewer has liked.
     *
     * @param viewerId the requesting viewer; a null viewer (anonymous) short-circuits to an empty
     *     set without querying
     * @param commentIds candidate comment ids; may contain duplicates
     * @return the subset the viewer has liked; empty when {@code viewerId} is null or {@code
     *     commentIds} is empty
     */
    Set<UUID> loadLikedCommentIds(UUID viewerId, Collection<UUID> commentIds);
}
