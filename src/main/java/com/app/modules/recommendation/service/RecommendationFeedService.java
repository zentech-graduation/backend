package com.app.modules.recommendation.service;

import java.util.UUID;

import com.app.common.response.CursorPageResponse;
import com.app.modules.post.dto.response.FeedPostResponse;

/** Personalized "for you" feed backed by the Gorse recommender. */
public interface RecommendationFeedService {

    /**
     * Returns a page of the personalized feed for the viewer.
     *
     * <p>Candidates come from Gorse; unavailability degrades to the popularity ranking and then to
     * the chronological following feed, so the endpoint keeps serving when the recommender is
     * down. Every returned post is published and visible to the viewer. Ranking scores are
     * populated on every entry served by the ranked sources and are null on chronological
     * fallback pages.
     *
     * @param viewerId authenticated user requesting the feed
     * @param cursor opaque cursor from a previous page; null for the first page
     * @param limit requested page size; values outside [1, 100] are normalized
     * @return one feed page with opaque continuation cursors
     */
    CursorPageResponse<FeedPostResponse> getRecommendedFeed(
            UUID viewerId, String cursor, int limit);
}
