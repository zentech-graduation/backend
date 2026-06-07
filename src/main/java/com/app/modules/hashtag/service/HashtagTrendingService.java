package com.app.modules.hashtag.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;

import com.app.common.response.PageResponse;
import com.app.modules.hashtag.dto.response.HashtagTrendingResponse;
import com.app.modules.hashtag.entity.HashtagTrending;

/** Computes and serves periodic hashtag trending snapshots. */
public interface HashtagTrendingService {

    /**
     * Aggregates hashtag usage within {@code [windowStart, windowEnd)} and writes ranked rows to
     * {@code hashtag_trending}.
     *
     * <p>Idempotent for a given {@code period_start}: re-running deletes the existing snapshot for
     * that period and re-inserts the current top ranks, so hashtags that dropped out of the top set
     * are removed rather than left as stale rows.
     *
     * @param windowStart the window lower bound, inclusive
     * @param windowEnd the window upper bound, exclusive
     * @return the persisted trending rows ordered by rank ascending
     */
    List<HashtagTrending> snapshotTrending(OffsetDateTime windowStart, OffsetDateTime windowEnd);

    /**
     * Returns the most recent trending snapshot page ordered by rank ascending.
     *
     * <p>Yields an empty page when no snapshot exists.
     *
     * @param pageable offset pagination request
     * @return the offset-paginated trending response
     */
    PageResponse<HashtagTrendingResponse> getTrending(Pageable pageable);
}
