package com.app.modules.hashtag.service.impl;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.response.PageResponse;
import com.app.modules.hashtag.config.HashtagProperties;
import com.app.modules.hashtag.dto.response.HashtagTrendingResponse;
import com.app.modules.hashtag.entity.Hashtag;
import com.app.modules.hashtag.entity.HashtagTrending;
import com.app.modules.hashtag.entity.HashtagTrendingId;
import com.app.modules.hashtag.mapper.HashtagMapper;
import com.app.modules.hashtag.repository.HashtagRepository;
import com.app.modules.hashtag.repository.HashtagTrendingRepository;
import com.app.modules.hashtag.service.HashtagTrendingService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class HashtagTrendingServiceImpl implements HashtagTrendingService {

    private static final int MAX_TRENDING = 100;

    private final HashtagTrendingRepository hashtagTrendingRepository;
    private final HashtagRepository hashtagRepository;
    private final HashtagMapper hashtagMapper;
    private final HashtagProperties properties;
    private final JdbcTemplate jdbcTemplate;

    public HashtagTrendingServiceImpl(
            HashtagTrendingRepository hashtagTrendingRepository,
            HashtagRepository hashtagRepository,
            HashtagMapper hashtagMapper,
            HashtagProperties properties,
            JdbcTemplate jdbcTemplate) {
        this.hashtagTrendingRepository = hashtagTrendingRepository;
        this.hashtagRepository = hashtagRepository;
        this.hashtagMapper = hashtagMapper;
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(
            fixedDelayString = "${app.hashtag.trending.job-delay:PT1H}",
            initialDelayString = "${app.hashtag.trending.job-initial-delay:PT5M}")
    @Transactional
    public void runTrendingJob() {
        OffsetDateTime windowEnd = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime windowStart = windowEnd.minus(properties.getTrending().getWindow());
        int written = snapshotTrending(windowStart, windowEnd).size();
        log.info(
                "Hashtag trending snapshot written: {} rows for window {} to {}",
                written,
                windowStart,
                windowEnd);
    }

    @Override
    @Transactional
    public List<HashtagTrending> snapshotTrending(
            OffsetDateTime windowStart, OffsetDateTime windowEnd) {
        List<TrendingCount> counts =
                jdbcTemplate.query(
                        "SELECT ph.hashtag_id, COUNT(ph.post_id) AS post_count"
                                + " FROM post_hashtags ph"
                                + " JOIN posts p ON p.id = ph.post_id"
                                + " WHERE p.created_at >= ? AND p.created_at < ? AND p.deleted_at IS NULL"
                                + " GROUP BY ph.hashtag_id"
                                + " ORDER BY post_count DESC"
                                + " LIMIT "
                                + MAX_TRENDING,
                        (rs, rowNum) ->
                                new TrendingCount(
                                        rs.getObject("hashtag_id", java.util.UUID.class),
                                        rs.getInt("post_count")),
                        windowStart,
                        windowEnd);

        List<HashtagTrending> rows = new ArrayList<>(counts.size());
        int rank = 1;
        for (TrendingCount c : counts) {
            rows.add(
                    HashtagTrending.builder()
                            .id(new HashtagTrendingId(c.hashtagId(), windowStart))
                            .periodEnd(windowEnd)
                            .postCount(c.postCount())
                            .rank(rank++)
                            .build());
        }
        return hashtagTrendingRepository.saveAll(rows);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<HashtagTrendingResponse> getTrending(Pageable pageable) {
        OffsetDateTime latest =
                jdbcTemplate.query(
                        "SELECT MAX(period_start) FROM hashtag_trending",
                        rs -> rs.next() ? rs.getObject(1, OffsetDateTime.class) : null);
        if (latest == null) {
            return PageResponse.from(Page.empty(pageable));
        }

        List<HashtagTrending> rows =
                hashtagTrendingRepository.findByIdPeriodStartOrderByRankAsc(latest, pageable);
        List<UUID> ids = rows.stream().map(r -> r.getId().getHashtagId()).toList();
        Map<UUID, String> names =
                hashtagRepository.findAllById(ids).stream()
                        .collect(Collectors.toMap(Hashtag::getId, Hashtag::getName));
        List<HashtagTrendingResponse> content =
                rows.stream()
                        .map(
                                r ->
                                        hashtagMapper.toTrendingResponse(
                                                r, names.get(r.getId().getHashtagId())))
                        .toList();

        long total = hashtagTrendingRepository.countByIdPeriodStart(latest);
        Page<HashtagTrendingResponse> page = new PageImpl<>(content, pageable, total);
        return PageResponse.from(page);
    }

    private record TrendingCount(UUID hashtagId, int postCount) {}
}
