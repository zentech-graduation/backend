package com.app.modules.post.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;

import com.app.common.response.CursorPageResponse;
import com.app.modules.post.dto.response.PostResponse;
import com.app.modules.post.repository.PostRepository;
import com.app.modules.post.search.PostDocument;
import com.app.modules.post.service.PostVisibilityService;

@ExtendWith(MockitoExtension.class)
class PostSearchServiceImplTest {

    @Mock private ElasticsearchOperations elasticsearchOperations;
    @Mock private PostRepository postRepository;
    @Mock private PostVisibilityService postVisibilityService;
    @Mock private PostResponseAssembler postResponseAssembler;
    @Mock private SearchHits<PostDocument> searchHits;

    private PostSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new PostSearchServiceImpl(
                        elasticsearchOperations,
                        postRepository,
                        postVisibilityService,
                        postResponseAssembler);
    }

    @Test
    void searchPosts_noHits_returnsEmptyPage() {
        when(searchHits.getSearchHits()).thenReturn(List.of());
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(PostDocument.class)))
                .thenReturn(searchHits);

        CursorPageResponse<PostResponse> page =
                service.searchPosts(UUID.randomUUID(), "cats", null, 20);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getPageInfo().getStartCursor()).isNull();
        assertThat(page.getPageInfo().getEndCursor()).isNull();
    }

    @Test
    void searchPosts_limitAboveMax_clampsElasticsearchPageSize() {
        when(searchHits.getSearchHits()).thenReturn(List.of());
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(PostDocument.class)))
                .thenReturn(searchHits);

        service.searchPosts(UUID.randomUUID(), "sunset", null, 10_000);

        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(elasticsearchOperations).search(captor.capture(), eq(PostDocument.class));
        // 100 (the clamp) + 1 (the over-fetch probe row used to derive hasNextPage).
        assertThat(captor.getValue().getPageable().getPageSize()).isEqualTo(101);
    }

    @Test
    void searchPosts_exactlyFullPage_hasNextPageIsFalse() {
        List<UUID> ids =
                java.util.stream.IntStream.range(0, 20).mapToObj(i -> UUID.randomUUID()).toList();
        stubEsHits(ids);
        stubVisibleAndAssembled(ids);

        CursorPageResponse<PostResponse> page =
                service.searchPosts(UUID.randomUUID(), "sunset", null, 20);

        assertThat(page.getContent()).hasSize(20);
        assertThat(page.getPageInfo().isHasNextPage()).isFalse();
    }

    @Test
    void searchPosts_moreThanFullPage_hasNextPageIsTrueAndTrimsToLimit() {
        List<UUID> ids =
                java.util.stream.IntStream.range(0, 21).mapToObj(i -> UUID.randomUUID()).toList();
        stubEsHits(ids);
        stubVisibleAndAssembled(ids.subList(0, 20));

        CursorPageResponse<PostResponse> page =
                service.searchPosts(UUID.randomUUID(), "sunset", null, 20);

        assertThat(page.getContent()).hasSize(20);
        assertThat(page.getPageInfo().isHasNextPage()).isTrue();
    }

    @SuppressWarnings("unchecked")
    private void stubEsHits(List<UUID> ids) {
        List<org.springframework.data.elasticsearch.core.SearchHit<PostDocument>> hits =
                ids.stream()
                        .map(
                                id -> {
                                    PostDocument doc = new PostDocument();
                                    doc.setId(id.toString());
                                    org.springframework.data.elasticsearch.core.SearchHit<
                                                    PostDocument>
                                            hit =
                                                    org.mockito.Mockito.mock(
                                                            org.springframework.data.elasticsearch
                                                                    .core.SearchHit.class);
                                    when(hit.getContent()).thenReturn(doc);
                                    return hit;
                                })
                        .toList();
        when(searchHits.getSearchHits()).thenReturn(hits);
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(PostDocument.class)))
                .thenReturn(searchHits);
    }

    private void stubVisibleAndAssembled(List<UUID> visibleIds) {
        List<com.app.modules.post.entity.Post> posts =
                visibleIds.stream()
                        .map(
                                id ->
                                        com.app.modules.post.entity.Post.builder()
                                                .id(id)
                                                .status(
                                                        com.app.modules.post.enums.PostStatus
                                                                .PUBLISHED)
                                                .build())
                        .toList();
        when(postRepository.findAllById(visibleIds)).thenReturn(posts);
        when(postVisibilityService.isVisibleTo(any(), any())).thenReturn(true);
        when(postResponseAssembler.assemble(any(), org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(
                        inv -> {
                            List<com.app.modules.post.entity.Post> input = inv.getArgument(1);
                            return input.stream().map(p -> (PostResponse) null).toList();
                        });
    }

    @Test
    void searchFallback_circuitResourceFailure_returnsEmptyPage() {
        CursorPageResponse<PostResponse> page =
                service.searchFallback(
                        UUID.randomUUID(),
                        "cats",
                        null,
                        20,
                        new DataAccessResourceFailureException("es down"));

        assertThat(page.getContent()).isEmpty();
        // An empty page from a degraded search must be distinguishable from a genuine no-match,
        // which it was not: the two responses differed only in their timestamp.
        assertThat(page.isDegraded()).isTrue();
    }

    @Test
    void searchFallback_ioCauseChain_returnsEmptyPage() {
        RuntimeException wrapped =
                new RuntimeException("transport", new IOException("connection reset"));

        CursorPageResponse<PostResponse> page =
                service.searchFallback(UUID.randomUUID(), "cats", null, 0, wrapped);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.isDegraded()).isTrue();
    }

    @Test
    void searchFallback_nonAvailabilityRuntimeException_rethrows() {
        IllegalArgumentException programmingError = new IllegalArgumentException("bad query");

        assertThatThrownBy(
                        () ->
                                service.searchFallback(
                                        UUID.randomUUID(), "cats", null, 20, programmingError))
                .isSameAs(programmingError);
    }

    @Test
    void searchFallback_nonAvailabilityCheckedException_wrapsInIllegalState() {
        Exception checked = new Exception("unexpected checked");

        assertThatThrownBy(
                        () -> service.searchFallback(UUID.randomUUID(), "cats", null, 20, checked))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(checked);
    }
}
