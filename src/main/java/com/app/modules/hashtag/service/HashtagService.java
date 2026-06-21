package com.app.modules.hashtag.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Manages canonical hashtags and their associations to posts. */
public interface HashtagService {

    /**
     * Normalizes a raw tag by stripping a leading {@code #}, trimming, and lowercasing.
     *
     * <p>Returns an empty string for a {@code null} or blank input so callers can drop it.
     *
     * @param raw the raw tag as supplied by the client
     * @return the normalized lowercase hashtag name, or an empty string when {@code raw} is null or
     *     blank
     */
    String normalize(String raw);

    /**
     * Normalizes and de-duplicates the supplied tags, upserts the hashtag rows, and associates them
     * to the post via {@code post_hashtags} rows in an idempotent manner.
     *
     * <p>For each affected hashtag an index-upsert event is enqueued in the transactional outbox,
     * propagating the change to Elasticsearch asynchronously after commit. The {@code
     * hashtags.post_count} counter is maintained by the Postgres trigger {@code
     * trg_hashtag_post_count} and is never written from application code.
     *
     * @param postId the post the hashtags are associated with
     * @param rawTags the raw tags to normalize and associate
     */
    void upsertHashtagsForPost(UUID postId, List<String> rawTags);

    /**
     * Removes all {@code post_hashtags} associations for the post; the Postgres trigger decrements
     * {@code hashtags.post_count} for each removed row.
     *
     * <p>Used on post unpublish or soft-delete. For each affected hashtag an outbox index event is
     * enqueued: an upsert when the hashtag still has associated posts, otherwise a delete event to
     * drop it from the search index.
     *
     * @param postId the post whose hashtag associations are removed
     */
    void removeHashtagsForPost(UUID postId);

    /**
     * Returns the hashtag ids associated with each of the given posts.
     *
     * <p>Posts without hashtag associations are absent from the returned map.
     *
     * @param postIds the posts to look up
     * @return hashtag ids grouped by post id; an empty map for empty input
     */
    Map<UUID, List<UUID>> getHashtagIdsForPosts(Collection<UUID> postIds);
}
