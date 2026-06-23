package com.app.modules.hashtag.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
}
