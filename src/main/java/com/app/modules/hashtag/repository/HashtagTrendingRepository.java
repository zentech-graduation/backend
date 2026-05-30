package com.app.modules.hashtag.repository;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.app.modules.hashtag.entity.HashtagTrending;
import com.app.modules.hashtag.entity.HashtagTrendingId;

@Repository
public interface HashtagTrendingRepository
        extends JpaRepository<HashtagTrending, HashtagTrendingId> {

    /**
     * Returns trending rows whose {@code period_start} equals the given instant, ordered by rank
     * ascending.
     *
     * @param periodStart the snapshot period start to match
     * @param pageable pagination and result-size bound
     * @return trending rows for the period ordered by rank ascending
     */
    List<HashtagTrending> findByIdPeriodStartOrderByRankAsc(
            OffsetDateTime periodStart, Pageable pageable);
}
