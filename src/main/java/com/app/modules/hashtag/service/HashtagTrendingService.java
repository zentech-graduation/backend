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
     * <p>Idempotent: re-running for the same period updates existing rows because the primary key
     * is {@code (hashtag_id, period_start)}.
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
