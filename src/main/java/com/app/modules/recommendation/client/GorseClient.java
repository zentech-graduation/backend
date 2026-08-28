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
     * Hides an existing item so it is never recommended again.
     *
     * <p>Only effective for an item Gorse already holds. Verified against v0.5.11: called with an
     * unknown id it answers {@code 200} with {@code RowAffected: 1} and stores nothing, so the item
     * stays absent and {@code auto_insert_item} later creates it visible from feedback alone.
     * Upsert the item with {@code IsHidden} set instead when its presence is not guaranteed.
     *
     * @param itemId item id to hide
     */
    void hideItem(String itemId);

    /**
     * Inserts feedback rows.
     *
     * <p>Row-idempotent in v0.5.11: re-inserting an existing (type, user, item) tuple keeps a
     * single row rather than duplicating it.
     *
     * <p>The row's {@code Value} is <b>accumulated, not overwritten</b>. Measured: inserting 2.0
     * then 5.0 for one tuple leaves one row holding 7.0. So a value is a running total for that
     * pair - the intended reading for dwell seconds - and a replay that reached this method twice
     * would inflate it. Redelivery is safe only because the inbox guard skips the handler on a
     * duplicate event id; do not weaken that guard on the assumption this call is fully idempotent.
     *
     * @param feedback feedback rows in Gorse wire format
     */
    void insertFeedback(List<GorseFeedback> feedback);
}
