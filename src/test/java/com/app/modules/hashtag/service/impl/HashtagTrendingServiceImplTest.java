package com.app.modules.hashtag.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import com.app.common.response.PageResponse;
import com.app.modules.hashtag.config.HashtagProperties;
import com.app.modules.hashtag.dto.response.HashtagTrendingResponse;
import com.app.modules.hashtag.entity.Hashtag;
import com.app.modules.hashtag.entity.HashtagTrending;
import com.app.modules.hashtag.entity.HashtagTrendingId;
import com.app.modules.hashtag.mapper.HashtagMapper;
import com.app.modules.hashtag.repository.HashtagRepository;
import com.app.modules.hashtag.repository.HashtagTrendingRepository;

@ExtendWith(MockitoExtension.class)
class HashtagTrendingServiceImplTest {

    @Mock private HashtagTrendingRepository hashtagTrendingRepository;
    @Mock private HashtagRepository hashtagRepository;
    @Mock private HashtagMapper hashtagMapper;
    @Mock private HashtagProperties properties;
    @Mock private JdbcTemplate jdbcTemplate;

    private HashtagTrendingServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new HashtagTrendingServiceImpl(
                        hashtagTrendingRepository,
                        hashtagRepository,
                        hashtagMapper,
                        properties,
                        jdbcTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getTrending_noSnapshot_returnsEmptyPageWithoutQueryingRows() {
        // No latest period exists: the MAX(period_start) extractor yields null.
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class))).thenReturn(null);

        PageResponse<HashtagTrendingResponse> page = service.getTrending(PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        verifyNoInteractions(hashtagTrendingRepository, hashtagRepository, hashtagMapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getTrending_withSnapshot_returnsHashtagContent() {
        OffsetDateTime latestPeriod = OffsetDateTime.now(ZoneOffset.UTC);
        UUID hashtagId = UUID.randomUUID();
        HashtagTrendingId trendingId = new HashtagTrendingId(hashtagId, latestPeriod);
        HashtagTrending row =
                HashtagTrending.builder().id(trendingId).rank(1).postCount(42).build();
        Hashtag hashtag = Hashtag.builder().id(hashtagId).name("java").build();
        HashtagTrendingResponse response =
                new HashtagTrendingResponse(hashtagId, "java", 42, 1, latestPeriod, latestPeriod);

        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class)))
                .thenReturn(latestPeriod);
        when(hashtagTrendingRepository.findByIdPeriodStartOrderByRankAsc(eq(latestPeriod), any()))
                .thenReturn(List.of(row));
        when(hashtagRepository.findAllById(List.of(hashtagId))).thenReturn(List.of(hashtag));
        when(hashtagTrendingRepository.countByIdPeriodStart(latestPeriod)).thenReturn(1L);
        when(hashtagMapper.toTrendingResponse(eq(row), eq("java"))).thenReturn(response);

        PageResponse<HashtagTrendingResponse> page = service.getTrending(PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0)).isEqualTo(response);
        assertThat(page.getTotalElements()).isEqualTo(1L);
        verify(hashtagMapper).toTrendingResponse(row, "java");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getTrending_withSnapshotButNoRows_returnsEmptyPage() {
        OffsetDateTime latestPeriod = OffsetDateTime.now(ZoneOffset.UTC);

        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class)))
                .thenReturn(latestPeriod);
        when(hashtagTrendingRepository.findByIdPeriodStartOrderByRankAsc(eq(latestPeriod), any()))
                .thenReturn(List.of());
        when(hashtagRepository.findAllById(List.of())).thenReturn(List.of());
        when(hashtagTrendingRepository.countByIdPeriodStart(latestPeriod)).thenReturn(0L);

        PageResponse<HashtagTrendingResponse> page = service.getTrending(PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        verifyNoInteractions(hashtagMapper);
    }
}
