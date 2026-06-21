package com.app.modules.post.service;

import java.util.UUID;

import com.app.common.response.CursorPageResponse;
import com.app.modules.post.dto.response.PostResponse;

/**
 * Post caption full-text search with Elasticsearch as the only search path.
 *
 * <p>When Elasticsearch is unavailable or the {@code elasticsearchSearch} circuit breaker is open,
 * an empty page is returned — post search has no PostgreSQL fallback by design.
 */
public interface PostSearchService {

    /**
     * Searches published posts by caption text.
     *
     * <p>Hits are hydrated from PostgreSQL, re-checked for published status (the index may lag),
     * and filtered by account-level visibility before mapping; a page may therefore contain fewer
     * items than requested.
     *
     * @param viewerId authenticated viewer
     * @param query search term matched against the caption and its ngram sub-field
     * @param cursor opaque base64 offset cursor; null or blank for the first page
     * @param size maximum results per page
     * @return cursor-paginated visible posts ranked by relevance
     */
    CursorPageResponse<PostResponse> searchPosts(
            UUID viewerId, String query, String cursor, int size);
}
