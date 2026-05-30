package com.app.modules.hashtag.service;

import java.util.List;
import java.util.UUID;

/** Manages canonical hashtags and their associations to posts. */
public interface HashtagService {

    /**
     * Normalizes a raw tag by stripping a leading {@code #}, trimming, and lowercasing.
     *
     * @param raw the raw tag as supplied by the client
     * @return the normalized lowercase hashtag name
     */
    String normalize(String raw);

    /**
     * Normalizes and de-duplicates the supplied tags, upserts the hashtag rows, and associates them
     * to the post via {@code post_hashtags} rows in an idempotent manner.
     *
     * <p>For each associated hashtag a post-commit Elasticsearch dual-write is registered. The
     * {@code hashtags.post_count} counter is maintained by the Postgres trigger {@code
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
     * <p>Used on post unpublish or soft-delete.
     *
     * @param postId the post whose hashtag associations are removed
     */
    void removeHashtagsForPost(UUID postId);
}
