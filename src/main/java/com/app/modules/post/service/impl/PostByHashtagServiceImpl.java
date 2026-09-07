package com.app.modules.post.service.impl;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.pagination.OffsetCursorCodec;
import com.app.common.pagination.OffsetPageable;
import com.app.common.response.CursorPageResponse;
import com.app.modules.hashtag.service.HashtagLookupService;
import com.app.modules.post.dto.response.PostResponse;
import com.app.modules.post.entity.Post;
import com.app.modules.post.enums.PostStatus;
import com.app.modules.post.repository.PostRepository;
import com.app.modules.post.search.PostDocument;
import com.app.modules.post.service.PostByHashtagService;
import com.app.modules.post.service.PostVisibilityService;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PostByHashtagServiceImpl implements PostByHashtagService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ElasticsearchOperations elasticsearchOperations;
    private final PostRepository postRepository;
    private final PostVisibilityService postVisibilityService;
    private final PostResponseAssembler postResponseAssembler;
    private final HashtagLookupService hashtagLookupService;
    private final PostByHashtagPostgresReader postgresReader;

    public PostByHashtagServiceImpl(
            ElasticsearchOperations elasticsearchOperations,
            PostRepository postRepository,
            PostVisibilityService postVisibilityService,
            PostResponseAssembler postResponseAssembler,
            HashtagLookupService hashtagLookupService,
            PostByHashtagPostgresReader postgresReader) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.postRepository = postRepository;
        this.postVisibilityService = postVisibilityService;
        this.postResponseAssembler = postResponseAssembler;
        this.hashtagLookupService = hashtagLookupService;
        this.postgresReader = postgresReader;
    }

    @Override
    @Transactional(readOnly = true)
    @CircuitBreaker(name = "elasticsearchSearch", fallbackMethod = "findPostsByHashtagFallback")
    public CursorPageResponse<PostResponse> findPostsByHashtag(
            UUID viewerId, UUID hashtagId, String cursor, int size) {
        // Runs before the Elasticsearch call so a banned or deleted hashtag is refused identically
        // on both tiers. Placing it inside the fallback as well would double the read; placing it
        // only there would let the primary path answer 200 for a tag the fallback refuses.
        hashtagLookupService.getById(hashtagId);
        int offset = OffsetCursorCodec.decode(cursor);
        int effectiveLimit = normalizeLimit(size);
        NativeQuery nativeQuery =
                NativeQuery.builder()
                        .withQuery(
                                q ->
                                        q.bool(
                                                b ->
                                                        b.filter(
                                                                        f ->
                                                                                f.term(
                                                                                        t ->
                                                                                                t.field(
                                                                                                                "hashtag_ids")
                                                                                                        .value(
                                                                                                                hashtagId
                                                                                                                        .toString())))
                                                                .filter(
                                                                        f ->
                                                                                f.term(
                                                                                        t ->
                                                                                                t.field(
                                                                                                                "status")
                                                                                                        .value(
                                                                                                                "published")))))
                        // Newest first rather than relevance: every document matches the term
                        // filter equally, so a relevance sort would be an arbitrary order that
                        // shifts between segment merges and silently reshuffles pages.
                        .withSort(Sort.by(Sort.Direction.DESC, "createdAt"))
                        .withPageable(new OffsetPageable(offset, effectiveLimit + 1))
                        .build();
        SearchHits<PostDocument> hits =
                elasticsearchOperations.search(nativeQuery, PostDocument.class);
        List<UUID> allIds =
                hits.getSearchHits().stream()
                        .map(SearchHit::getContent)
                        .map(PostDocument::getId)
                        .map(UUID::fromString)
                        .toList();
        // Over-fetch by one and use its presence as the hasNextPage signal, rather than inferring
        // it from a full page, which is indistinguishable from an exhausted result set on the last
        // page and forces the client into one extra, always-empty request.
        boolean hasNextPage = allIds.size() > effectiveLimit;
        List<UUID> ids = hasNextPage ? allIds.subList(0, effectiveLimit) : allIds;
        return toPage(hydrateVisible(viewerId, ids), offset, hasNextPage, ids.size());
    }

    // Resilience4j fallback: invoked when the primary throws OR the circuit is open. Unlike caption
    // search, this read has a real lower tier - post_hashtags is the source of truth for hashtag
    // membership - so it degrades to a PostgreSQL join rather than to an empty page, and the result
    // is complete rather than degraded.
    CursorPageResponse<PostResponse> findPostsByHashtagFallback(
            UUID viewerId, UUID hashtagId, String cursor, int size, Throwable t) {
        // Only availability failures may degrade to PostgreSQL; programming or data errors, and the
        // lifecycle refusal raised by the primary, must surface to the caller instead of being
        // masked as degradation.
        if (!isAvailabilityFailure(t)) {
            if (t instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Unexpected posts-by-hashtag failure", t);
        }
        log.warn(
                "Posts-by-hashtag Elasticsearch read unavailable ({}: {}), using PostgreSQL join",
                t.getClass().getSimpleName(),
                t.getMessage(),
                t);
        // An open circuit means the method body never ran, so the lifecycle gate has not been
        // applied yet and has to be applied here.
        if (t instanceof CallNotPermittedException) {
            hashtagLookupService.getById(hashtagId);
        }
        return postgresReader.read(
                viewerId, hashtagId, OffsetCursorCodec.decode(cursor), normalizeLimit(size));
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
                        .filter(p -> p.getDeletedAt() == null)
                        .filter(p -> postVisibilityService.isVisibleTo(viewerId, p))
                        .collect(Collectors.toMap(Post::getId, Function.identity()));
        // Preserve the Elasticsearch ordering, which is the newest-first sort applied above.
        List<Post> ordered = ids.stream().map(byId::get).filter(p -> p != null).toList();
        return ordered.isEmpty() ? List.of() : postResponseAssembler.assemble(viewerId, ordered);
    }

    // Mirrors PostSearchServiceImpl: Spring Data Elasticsearch translates transport failures to
    // DataAccessResourceFailureException, raw client failures surface as IOException in the cause
    // chain, and CallNotPermittedException means the circuit is already open. Any exception from
    // Spring Data Elasticsearch's own package is the server rejecting a request rather than a
    // connectivity problem, but the index is still a rebuildable tier per GLOBAL_RULES.md and must
    // degrade the same way. PostgreSQL failures throw JPA/Hibernate types, not these, so a genuine
    // data-layer bug still surfaces as a real failure.
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

    private CursorPageResponse<PostResponse> toPage(
            List<PostResponse> content, int offset, boolean hasNextPage, int rawHitCount) {
        String start = content.isEmpty() ? null : OffsetCursorCodec.encode(offset);
        // Advances by the pre-filter hit count, not the visible count, so posts hidden by the
        // visibility chain are never re-fetched on the next page.
        String end = content.isEmpty() ? null : OffsetCursorCodec.encode(offset + rawHitCount);
        return CursorPageResponse.of(content, hasNextPage, start, end, offset > 0);
    }
}
