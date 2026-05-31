package com.app.modules.hashtag.service.impl;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import com.app.common.response.CursorPageResponse;
import com.app.modules.hashtag.dto.response.HashtagResponse;
import com.app.modules.hashtag.mapper.HashtagMapper;
import com.app.modules.hashtag.repository.HashtagRepository;
import com.app.modules.hashtag.search.HashtagDocument;
import com.app.modules.hashtag.service.HashtagSearchService;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class HashtagSearchServiceImpl implements HashtagSearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final HashtagRepository hashtagRepository;
    private final HashtagMapper hashtagMapper;

    public HashtagSearchServiceImpl(
            ElasticsearchOperations elasticsearchOperations,
            HashtagRepository hashtagRepository,
            HashtagMapper hashtagMapper) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.hashtagRepository = hashtagRepository;
        this.hashtagMapper = hashtagMapper;
    }

    @Override
    @CircuitBreaker(name = "elasticsearchSearch", fallbackMethod = "searchFallback")
    public CursorPageResponse<HashtagResponse> search(String query, String cursor, int limit) {
        String normalized = normalizeQuery(query);
        int offset = decodeCursor(cursor);
        int page = limit > 0 ? offset / limit : 0;
        NativeQuery nativeQuery =
                NativeQuery.builder()
                        .withQuery(q -> q.match(m -> m.field("name.ngram").query(normalized)))
                        .withPageable(PageRequest.of(page, limit > 0 ? limit : 20))
                        .build();
        SearchHits<HashtagDocument> hits =
                elasticsearchOperations.search(nativeQuery, HashtagDocument.class);
        List<HashtagResponse> content =
                hits.getSearchHits().stream()
                        .map(SearchHit::getContent)
                        .map(hashtagMapper::fromDocument)
                        .toList();
        return toPage(content, offset, limit);
    }

    // Resilience4j fallback: invoked when the primary throws OR the circuit is open. Falls back to
    // the PostgreSQL pg_trgm GIN index so search degrades gracefully when Elasticsearch is down.
    CursorPageResponse<HashtagResponse> searchFallback(
            String query, String cursor, int limit, Throwable t) {
        log.warn(
                "Hashtag Elasticsearch search unavailable, using pg_trgm fallback: {}",
                t.getMessage());
        String normalized = normalizeQuery(query);
        int offset = decodeCursor(cursor);
        List<HashtagResponse> content =
                hashtagRepository.searchByNameTrgm(normalized, limit, offset).stream()
                        .map(hashtagMapper::toResponse)
                        .toList();
        return toPage(content, offset, limit);
    }

    private String normalizeQuery(String raw) {
        String trimmed = raw.strip();
        String withoutHash = trimmed.replaceFirst("^#+", "");
        return withoutHash.strip().toLowerCase(Locale.ROOT);
    }

    private CursorPageResponse<HashtagResponse> toPage(
            List<HashtagResponse> content, int offset, int limit) {
        String start = content.isEmpty() ? null : encodeCursor(offset);
        String end = content.isEmpty() ? null : encodeCursor(offset + content.size());
        return CursorPageResponse.of(content, limit, start, end, offset > 0);
    }

    private static String encodeCursor(int offset) {
        return Base64.getEncoder()
                .encodeToString(Integer.toString(offset).getBytes(StandardCharsets.UTF_8));
    }

    private static int decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(
                    new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            // Malformed cursor → start from the first page rather than failing the request.
            return 0;
        }
    }
}
