package com.app.modules.recommendation.service;

import java.util.UUID;

import com.app.common.response.CursorPageResponse;
import com.app.modules.post.dto.response.FeedPostResponse;

/** Personalized "for you" feed backed by the Gorse recommender. */
public interface RecommendationFeedService {

    /**
     * Returns a page of the personalized feed for the viewer.
     *
     * <p>Candidates come from Gorse, backfilled from trending once Gorse's own list runs short;
     * unavailability degrades to the popularity ranking. When {@code excludeFollowed} is false and
     * both ranked sources are empty on the first page, this falls further back to the chronological
     * following feed. When {@code excludeFollowed} is true that fallback is suppressed - the
     * following feed is, by definition, exactly the accounts an Explore-style caller asked to
     * exclude - so an exhausted Explore result is an honest empty page instead. Every returned post
     * is published and visible to the viewer. Ranking scores are populated on every entry served by
     * the ranked sources and are null on chronological fallback pages.
     *
     * @param viewerId authenticated user requesting the feed
     * @param cursor opaque cursor from a previous page; null for the first page
     * @param limit requested page size; values outside [1, 100] are normalized
     * @param excludeFollowed when true, removes candidates authored by accounts the viewer already
     *     follows and suppresses the chronological-following fallback; serves the discovery
     *     ("Explore") variant of this feed
     * @return one feed page with opaque continuation cursors
     */
    CursorPageResponse<FeedPostResponse> getRecommendedFeed(
            UUID viewerId, String cursor, int limit, boolean excludeFollowed);
}
