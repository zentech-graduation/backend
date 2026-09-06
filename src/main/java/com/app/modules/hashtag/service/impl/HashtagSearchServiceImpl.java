package com.app.modules.hashtag.service.impl;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import com.app.common.pagination.OffsetCursorCodec;
import com.app.common.pagination.OffsetPageable;
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
        int offset = OffsetCursorCodec.decode(cursor);
        int effectiveLimit = limit > 0 ? limit : 20;
        // Page the ES query by the exact cursor offset. PageRequest derives `from` as page * size,
        // which truncates any offset not divisible by the page size (e.g. when the client varies
        // the limit between requests) and silently skips or repeats results.
        NativeQuery nativeQuery =
                NativeQuery.builder()
                        .withQuery(q -> q.match(m -> m.field("name.ngram").query(normalized)))
                        .withPageable(new OffsetPageable(offset, effectiveLimit + 1))
                        .build();
        SearchHits<HashtagDocument> hits =
                elasticsearchOperations.search(nativeQuery, HashtagDocument.class);
        List<HashtagResponse> allContent =
                hits.getSearchHits().stream()
                        .map(SearchHit::getContent)
                        .map(hashtagMapper::fromDocument)
                        .toList();
        // Over-fetch by one and use its presence as the hasNextPage signal, rather than inferring
        // it from a full page, which is indistinguishable from an exhausted result set on the last
        // page and forces the client into one extra, always-empty request.
        boolean hasNextPage = allContent.size() > effectiveLimit;
        List<HashtagResponse> content =
                hasNextPage ? allContent.subList(0, effectiveLimit) : allContent;
        return toPage(content, offset, hasNextPage);
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
        int offset = OffsetCursorCodec.decode(cursor);
        List<HashtagResponse> allContent =
                hashtagRepository.searchByNameTrgm(normalized, limit + 1, offset).stream()
                        .map(hashtagMapper::toResponse)
                        .toList();
        boolean hasNextPage = allContent.size() > limit;
        List<HashtagResponse> content = hasNextPage ? allContent.subList(0, limit) : allContent;
        return toPage(content, offset, hasNextPage);
    }

    // Spring Data Elasticsearch translates transport/connection failures to
    // DataAccessResourceFailureException; raw client failures surface as IOException in the cause
    // chain. CallNotPermittedException means the circuit is already open. Any exception type from
    // Spring Data Elasticsearch's own package (UncategorizedElasticsearchException,
    // NoSuchIndexException, RestStatusException, etc.) is the server rejecting or failing a
    // request — auth, index state, version mismatch — not a connectivity problem, but the search
    // index is still a rebuildable tier per GLOBAL_RULES.md and must degrade to pg_trgm the same
    // way.
    private static boolean isAvailabilityFailure(Throwable t) {
        if (t instanceof CallNotPermittedException) {
            return true;
        }
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause instanceof DataAccessResourceFailureException
                    || cause instanceof IOException
                    || cause.getClass()
                            .getName()
                            .startsWith("org.springframework.data.elasticsearch.")) {
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
            List<HashtagResponse> content, int offset, boolean hasNextPage) {
        String start = content.isEmpty() ? null : OffsetCursorCodec.encode(offset);
        String end = content.isEmpty() ? null : OffsetCursorCodec.encode(offset + content.size());
        return CursorPageResponse.of(content, hasNextPage, start, end, offset > 0);
    }
}
