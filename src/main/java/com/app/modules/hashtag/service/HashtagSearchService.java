package com.app.modules.hashtag.service;

import com.app.common.response.CursorPageResponse;
import com.app.modules.hashtag.dto.response.HashtagResponse;

/**
 * Hashtag fuzzy search with Elasticsearch as the primary path and PostgreSQL {@code pg_trgm} as the
 * automatic fallback when the {@code elasticsearchSearch} circuit breaker is open or Elasticsearch
 * is unavailable.
 */
public interface HashtagSearchService {

    /**
     * Searches hashtags by fuzzy name matching.
     *
     * @param query search term (normalized before querying)
     * @param cursor opaque base64 cursor from the previous page; {@code null}/blank for first page
     * @param limit maximum results per page
     * @return cursor-paginated hashtag list
     */
    CursorPageResponse<HashtagResponse> search(String query, String cursor, int limit);
}
