package com.app.modules.hashtag.service.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.response.CursorPageResponse;
import com.app.modules.hashtag.dto.response.HashtagResponse;
import com.app.modules.hashtag.mapper.HashtagMapper;
import com.app.modules.hashtag.repository.HashtagRepository;
import com.app.modules.hashtag.search.HashtagDocument;
import com.app.modules.hashtag.service.HashtagSearchService;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class HashtagSearchServiceImpl implements HashtagSearchService {

    // Out-of-range offsets reset to the first page rather than reaching ES/pg_trgm, where a deep
    // `from`/OFFSET triggers expensive scans and counts as a circuit-breaker failure.
    private static final int MAX_SEARCH_OFFSET = 10_000;

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
        int effectiveLimit = limit > 0 ? limit : 20;
        // Page the ES query by the exact cursor offset. PageRequest derives `from` as page * size,
        // which truncates any offset not divisible by the page size (e.g. when the client varies
        // the limit between requests) and silently skips or repeats results.
        NativeQuery nativeQuery =
                NativeQuery.builder()
                        .withQuery(q -> q.match(m -> m.field("name.ngram").query(normalized)))
                        .withPageable(new OffsetPageable(offset, effectiveLimit))
                        .build();
        SearchHits<HashtagDocument> hits =
                elasticsearchOperations.search(nativeQuery, HashtagDocument.class);
        List<HashtagResponse> content =
                hits.getSearchHits().stream()
                        .map(SearchHit::getContent)
                        .map(hashtagMapper::fromDocument)
                        .toList();
        return toPage(content, offset, effectiveLimit);
    }

    // Resilience4j fallback: invoked when the primary throws OR the circuit is open. Falls back to
    // the PostgreSQL pg_trgm GIN index so search degrades gracefully when Elasticsearch is down.
    CursorPageResponse<HashtagResponse> searchFallback(
            String query, String cursor, int limit, Throwable t) {
        // Only availability failures may degrade to pg_trgm; programming or data errors must
        // surface to the caller instead of being masked as degradation.
        if (!isAvailabilityFailure(t)) {
            if (t instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Unexpected hashtag search failure", t);
        }
        log.warn(
                "Hashtag Elasticsearch search unavailable ({}: {}), using pg_trgm fallback",
                t.getClass().getSimpleName(),
                t.getMessage(),
                t);
        String normalized = normalizeQuery(query);
        int offset = decodeCursor(cursor);
        List<HashtagResponse> content =
                hashtagRepository.searchByNameTrgm(normalized, limit, offset).stream()
                        .map(hashtagMapper::toResponse)
                        .toList();
        return toPage(content, offset, limit);
    }

    // Spring Data Elasticsearch translates transport/connection failures to
    // DataAccessResourceFailureException; raw client failures surface as IOException in the cause
    // chain. CallNotPermittedException means the circuit is already open.
    private static boolean isAvailabilityFailure(Throwable t) {
        if (t instanceof CallNotPermittedException) {
            return true;
        }
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause instanceof DataAccessResourceFailureException
                    || cause instanceof IOException) {
                return true;
            }
        }
        return false;
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
        return CursorPageResponse.of(content, content.size() == limit, start, end, offset > 0);
    }

    private static String encodeCursor(int offset) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(Integer.toString(offset).getBytes(StandardCharsets.UTF_8));
    }

    private static int decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            int offset =
                    Integer.parseInt(
                            new String(
                                    Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
            // Reject negative or excessively deep offsets so a forged cursor cannot drive a deep ES
            // `from` / pg_trgm OFFSET that errors and trips the circuit breaker.
            if (offset < 0 || offset > MAX_SEARCH_OFFSET) {
                throw new NumberFormatException("offset out of range");
            }
            return offset;
        } catch (RuntimeException e) {
            throw new AppException(ApiErrorCode.INVALID_CURSOR);
        }
    }

    // Pageable whose `from` is the exact cursor offset, not page * size, so cursor paging stays
    // correct when the offset is not a multiple of the page size.
    private record OffsetPageable(int offset, int size) implements Pageable {

        @Override
        public int getPageNumber() {
            return size > 0 ? offset / size : 0;
        }

        @Override
        public int getPageSize() {
            return size;
        }

        @Override
        public long getOffset() {
            return offset;
        }

        @Override
        public Sort getSort() {
            return Sort.unsorted();
        }

        @Override
        public Pageable next() {
            return new OffsetPageable(offset + size, size);
        }

        @Override
        public Pageable previousOrFirst() {
            return offset > 0 ? new OffsetPageable(Math.max(0, offset - size), size) : this;
        }

        @Override
        public Pageable first() {
            return new OffsetPageable(0, size);
        }

        @Override
        public Pageable withPage(int pageNumber) {
            return new OffsetPageable(pageNumber * size, size);
        }

        @Override
        public boolean hasPrevious() {
            return offset > 0;
        }
    }
}
