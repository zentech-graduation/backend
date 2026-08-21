package com.app.modules.recommendation.client;

import java.util.List;
import java.util.UUID;

import com.app.modules.recommendation.client.dto.GorseFeedback;
import com.app.modules.recommendation.client.dto.GorseItem;
import com.app.modules.recommendation.client.dto.GorseScore;
import com.app.modules.recommendation.client.dto.GorseUser;

/**
 * REST client contract for the Gorse recommender service (v0.5.11).
 *
 * <p>Methods propagate {@link org.springframework.web.client.RestClientException} on transport or
 * server failure; resilience (circuit breaking on the read path, retry/dead-letter on the write
 * path) is the caller's responsibility.
 */
public interface GorseClient {

    /**
     * Fetches personalized recommendations for a user.
     *
     * <p>Users unknown to Gorse are served by its configured fallback recommenders, so this call
     * also covers cold-start users.
     *
     * @param userId app user id
     * @param n maximum number of items to return
     * @param offset zero-based offset into the cached recommendation list
     * @return scored item ids ordered best-first; empty when Gorse has nothing cached
     */
    List<GorseScore> recommend(UUID userId, int n, int offset);

    /**
     * Fetches the non-personalized popularity ranking (recommender name {@code popular}).
     *
     * @param n maximum number of items to return
     * @param offset zero-based offset into the popularity list
     * @return scored item ids ordered by popularity; empty when not yet computed
     */
    List<GorseScore> popular(int n, int offset);

    /**
     * Inserts or updates users.
     *
     * @param users users in Gorse wire format
     */
    void upsertUsers(List<GorseUser> users);

    /**
     * Inserts or updates items.
     *
     * @param items items in Gorse wire format
     */
    void upsertItems(List<GorseItem> items);

    /**
     * Hides an item so it is never recommended again; safe to call for unknown items.
     *
     * @param itemId item id to hide
     */
    void hideItem(String itemId);

    /**
     * Inserts feedback rows.
     *
     * <p>Verified idempotent in v0.5.11: re-inserting an existing (type, user, item) tuple
     * overwrites rather than duplicates, so redelivery-driven replays are safe.
     *
     * @param feedback feedback rows in Gorse wire format
     */
    void insertFeedback(List<GorseFeedback> feedback);
}
