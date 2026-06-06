package com.app.modules.hashtag.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;

import com.app.common.response.CursorPageResponse;
import com.app.modules.hashtag.dto.response.HashtagResponse;
import com.app.modules.hashtag.entity.Hashtag;
import com.app.modules.hashtag.mapper.HashtagMapper;
import com.app.modules.hashtag.repository.HashtagRepository;
import com.app.modules.hashtag.search.HashtagDocument;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

@ExtendWith(MockitoExtension.class)
class HashtagSearchServiceImplTest {

    @Mock private ElasticsearchOperations elasticsearchOperations;
    @Mock private HashtagRepository hashtagRepository;
    @Mock private HashtagMapper hashtagMapper;

    private HashtagSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new HashtagSearchServiceImpl(
                        elasticsearchOperations, hashtagRepository, hashtagMapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_elasticsearchPrimaryPath_returnsMappedResults() {
        HashtagDocument doc =
                HashtagDocument.builder()
                        .id(UUID.randomUUID().toString())
                        .name("spring")
                        .postCount(10)
                        .build();
        SearchHit<HashtagDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(doc);
        SearchHits<HashtagDocument> hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of(hit));
        when(elasticsearchOperations.search(any(Query.class), eq(HashtagDocument.class)))
                .thenReturn(hits);
        HashtagResponse mapped = new HashtagResponse(UUID.randomUUID(), "spring", 10, null);
        when(hashtagMapper.fromDocument(doc)).thenReturn(mapped);

        CursorPageResponse<HashtagResponse> result = service.search("spring", null, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("spring");
        verify(hashtagRepository, never()).searchByNameTrgm(anyString(), anyInt(), anyInt());
    }

    @Test
    void searchFallback_circuitOpen_usesPostgresTrgm() {
        UUID id = UUID.randomUUID();
        Hashtag hashtag = Hashtag.builder().id(id).name("java").build();
        when(hashtagRepository.searchByNameTrgm("java", 20, 0)).thenReturn(List.of(hashtag));
        HashtagResponse mapped = new HashtagResponse(id, "java", 5, null);
        when(hashtagMapper.toResponse(hashtag)).thenReturn(mapped);

        CallNotPermittedException circuitOpen =
                CallNotPermittedException.createCallNotPermittedException(
                        CircuitBreaker.ofDefaults("elasticsearchSearch"));

        CursorPageResponse<HashtagResponse> result =
                service.searchFallback("java", null, 20, circuitOpen);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("java");
        verify(hashtagRepository).searchByNameTrgm("java", 20, 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_noMatches_returnsEmptyPageWithoutNextPage() {
        SearchHits<HashtagDocument> hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of());
        when(elasticsearchOperations.search(any(Query.class), eq(HashtagDocument.class)))
                .thenReturn(hits);

        CursorPageResponse<HashtagResponse> result = service.search("nope", null, 20);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getPageInfo().isHasNextPage()).isFalse();
    }
}
