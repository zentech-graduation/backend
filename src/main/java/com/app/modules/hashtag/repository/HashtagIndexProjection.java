package com.app.modules.hashtag.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read projection of a hashtag for search-index synchronization.
 *
 * <p>Exposes scalar columns only so the query reads current database values within the transaction
 * rather than a managed entity served from the Hibernate L1 cache; this guarantees the
 * trigger-updated {@code post_count} is visible after an {@code EntityManager.flush()}.
 */
public interface HashtagIndexProjection {

    UUID getId();

    String getName();

    int getPostCount();

    OffsetDateTime getCreatedAt();
}
