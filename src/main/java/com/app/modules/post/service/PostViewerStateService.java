package com.app.modules.post.service;

import java.util.Collection;
import java.util.UUID;

/**
 * Resolves the requesting viewer's like and save membership over a page of posts in two batched
 * queries.
 *
 * <p>The single point every post response uses to embed {@code isLiked} and {@code isSaved}, so
 * every list endpoint pays a constant, page-size-independent query cost instead of one probe per
 * row.
 */
public interface PostViewerStateService {

    /**
     * Loads the viewer's like and save membership over the given posts.
     *
     * @param viewerId the requesting viewer; a null viewer (anonymous) short-circuits to {@link
     *     PostViewerState#NONE} without querying
     * @param postIds candidate post ids; may contain duplicates
     * @return the viewer's liked and saved subsets; {@link PostViewerState#NONE} when {@code
     *     viewerId} is null or {@code postIds} is empty
     */
    PostViewerState load(UUID viewerId, Collection<UUID> postIds);
}
