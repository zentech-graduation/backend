package com.app.modules.post.service.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.response.CursorPageResponse;
import com.app.modules.post.dto.response.PostResponse;
import com.app.modules.post.entity.Post;
import com.app.modules.post.enums.PostStatus;
import com.app.modules.post.repository.PostRepository;
import com.app.modules.post.search.PostDocument;
import com.app.modules.post.service.PostSearchService;
import com.app.modules.post.service.PostVisibilityService;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

/**
 * Elasticsearch-backed post search.
 *
 * <p>Diverges from the hashtag module deliberately: availability failures degrade to an empty page
 * instead of a PostgreSQL fallback (Phase A decision).
 */
@Slf4j
@Service
public class PostSearchServiceImpl implements PostSearchService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ElasticsearchOperations elasticsearchOperations;
    private final PostRepository postRepository;
    private final PostVisibilityService postVisibilityService;
    private final PostResponseAssembler postResponseAssembler;

    public PostSearchServiceImpl(
            ElasticsearchOperations elasticsearchOperations,
            PostRepository postRepository,
            PostVisibilityService postVisibilityService,
            PostResponseAssembler postResponseAssembler) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.postRepository = postRepository;
        this.postVisibilityService = postVisibilityService;
        this.postResponseAssembler = postResponseAssembler;
    }

    @Override
    @Transactional(readOnly = true)
    @CircuitBreaker(name = "elasticsearchSearch", fallbackMethod = "searchFallback")
    public CursorPageResponse<PostResponse> searchPosts(
            UUID viewerId, String query, String cursor, int size) {
        int offset = decodeCursor(cursor);
        int effectiveLimit = normalizeLimit(size);
        // Page the ES query by the exact cursor offset. PageRequest derives `from` as page * size,
        // which truncates any offset not divisible by the page size (e.g. when the client varies
        // the limit between requests) and silently skips or repeats results.
        NativeQuery nativeQuery =
                NativeQuery.builder()
                        .withQuery(
                                q ->
                                        q.bool(
                                                b ->
                                                        b.should(
                                                                        s ->
                                                                                s.match(
                                                                                        m ->
                                                                                                m.field(
                                                                                                                "caption")
                                                                                                        .query(
                                                                                                                query)))
                                                                .should(
                                                                        s ->
                                                                                s.match(
                                                                                        m ->
                                                                                                m.field(
                                                                                                                "caption.ngram")
                                                                                                        .query(
                                                                                                                query)))
                                                                .minimumShouldMatch("1")
                                                                .filter(
                                                                        f ->
                                                                                f.term(
                                                                                        t ->
                                                                                                t.field(
                                                                                                                "status")
                                                                                                        .value(
                                                                                                                "published")))))
                        .withPageable(new OffsetPageable(offset, effectiveLimit))
                        .build();
        SearchHits<PostDocument> hits =
                elasticsearchOperations.search(nativeQuery, PostDocument.class);
        List<UUID> ids =
                hits.getSearchHits().stream()
                        .map(SearchHit::getContent)
                        .map(PostDocument::getId)
                        .map(UUID::fromString)
                        .toList();
        List<PostResponse> content = hydrateVisible(viewerId, ids);
        // The next-page cursor advances by the ES hit count, not the post-filter count, so hidden
        // hits are never re-fetched.
        return toPage(content, offset, effectiveLimit, ids.size());
    }

    // Resilience4j fallback: invoked when the primary throws OR the circuit is open. Returns an
    // empty page — post search intentionally has no PostgreSQL fallback.
    CursorPageResponse<PostResponse> searchFallback(
            UUID viewerId, String query, String cursor, int size, Throwable t) {
        // Only availability failures may degrade to an empty page; programming or data errors
        // must surface to the caller instead of being masked as degradation.
        if (!isAvailabilityFailure(t)) {
            if (t instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Unexpected post search failure", t);
        }
        log.warn(
                "Post Elasticsearch search unavailable ({}: {}), returning empty page",
                t.getClass().getSimpleName(),
                t.getMessage(),
                t);
        int effectiveLimit = normalizeLimit(size);
        return CursorPageResponse.of(List.of(), effectiveLimit, null, null, false);
    }

    // Clamp the page size so an oversized request cannot force a mass hydration or exceed the
    // Elasticsearch result-window ceiling, matching the bound applied by the other list endpoints.
    private static int normalizeLimit(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private List<PostResponse> hydrateVisible(UUID viewerId, List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, Post> byId =
                postRepository.findAllById(ids).stream()
                        // Stale-index guard: only posts still published in PostgreSQL qualify.
                        .filter(p -> p.getStatus() == PostStatus.PUBLISHED)
                        .filter(p -> postVisibilityService.isVisibleTo(viewerId, p))
                        .collect(Collectors.toMap(Post::getId, Function.identity()));
        // Preserve Elasticsearch relevance ordering.
        List<Post> ordered = ids.stream().map(byId::get).filter(p -> p != null).toList();
        return postResponseAssembler.assemble(ordered);
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

    private CursorPageResponse<PostResponse> toPage(
            List<PostResponse> content, int offset, int limit, int esHitCount) {
        String start = content.isEmpty() ? null : encodeCursor(offset);
        String end = content.isEmpty() ? null : encodeCursor(offset + esHitCount);
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
