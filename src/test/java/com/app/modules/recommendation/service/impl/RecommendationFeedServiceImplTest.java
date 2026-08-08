package com.app.modules.recommendation.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.common.response.CursorPageResponse;
import com.app.modules.post.dto.response.FeedPostResponse;
import com.app.modules.post.entity.Post;
import com.app.modules.post.enums.PostStatus;
import com.app.modules.post.enums.PostType;
import com.app.modules.post.service.PostLookupService;
import com.app.modules.post.service.PostService;
import com.app.modules.post.service.PostVisibilityService;
import com.app.modules.recommendation.client.dto.GorseScore;
import com.app.modules.recommendation.config.GorseProperties;
import com.app.modules.recommendation.service.impl.feed.RecommendationSource;
import com.app.modules.recommendation.service.impl.feed.RecommendationSource.SourceBatch;

@ExtendWith(MockitoExtension.class)
class RecommendationFeedServiceImplTest {

    @Mock private RecommendationSource recommendationSource;
    @Mock private PostLookupService postLookupService;
    @Mock private PostVisibilityService postVisibilityService;
    @Mock private PostService postService;

    private GorseProperties gorseProperties;
    private RecommendationFeedServiceImpl service;

    private final UUID viewerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        gorseProperties = new GorseProperties();
        gorseProperties.setRecommendMultiplier(2);
        service =
                new RecommendationFeedServiceImpl(
                        recommendationSource,
                        postLookupService,
                        postVisibilityService,
                        postService,
                        gorseProperties);
        // Feed assembly is a pass-through in these tests; only ranking-score attachment matters.
        // Fallback-path tests never reach the assembler, so this stub is lenient.
        lenient()
                .when(postLookupService.assembleFeed(any(UUID.class), anyList()))
                .thenAnswer(
                        inv -> {
                            List<Post> posts = inv.getArgument(1);
                            return posts.stream().map(p -> feedResponse(p.getId())).toList();
                        });
    }

    private Post publishedPost(UUID id, UUID ownerId) {
        return Post.builder().id(id).userId(ownerId).status(PostStatus.PUBLISHED).build();
    }

    private FeedPostResponse feedResponse(UUID id) {
        return new FeedPostResponse(
                id,
                null,
                "caption",
                PostType.TEXT,
                PostStatus.PUBLISHED,
                0,
                0,
                0,
                false,
                false,
                0,
                null,
                null,
                null,
                List.of(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null);
    }

    private static String decodeCursor(String cursor) {
        return new String(Base64.getDecoder().decode(cursor));
    }

    @Test
    void getRecommendedFeed_nullCursor_fetchesGorseFromOffsetZero() {
        // limit=1 so the single returned candidate fills the page exactly - otherwise the
        // pipeline would fetch a second round that this test does not stub.
        UUID postId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Post post = publishedPost(postId, ownerId);
        when(recommendationSource.fetch(eq(viewerId), eq('g'), eq(2), eq(0)))
                .thenReturn(new SourceBatch('g', List.of(new GorseScore(postId.toString(), 5.0))));
        when(postLookupService.findActiveByIds(anyList())).thenReturn(List.of(post));
        when(postVisibilityService.filterVisibleOwnerIds(viewerId, Set.of(ownerId)))
                .thenReturn(Set.of(ownerId));

        CursorPageResponse<FeedPostResponse> page = service.getRecommendedFeed(viewerId, null, 1);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).rankingScore()).isEqualTo(5.0);
        verify(recommendationSource).fetch(viewerId, 'g', 2, 0);
        verify(postService, never()).getFeed(any(), any(), anyInt());
    }

    @Test
    void getRecommendedFeed_malformedRankedCursor_delegatesToChronologicalFeedUnchanged() {
        String garbageCursor = "not-a-valid-ranked-cursor!!!";
        CursorPageResponse<FeedPostResponse> chronoPage =
                CursorPageResponse.of(List.of(), false, null, null, true);
        when(postService.getFeed(viewerId, garbageCursor, 20)).thenReturn(chronoPage);

        CursorPageResponse<FeedPostResponse> page =
                service.getRecommendedFeed(viewerId, garbageCursor, 20);

        assertThat(page).isSameAs(chronoPage);
        verify(recommendationSource, never()).fetch(any(), anyChar(), anyInt(), anyInt());
    }

    @Test
    void getRecommendedFeed_ownedAndInvisiblePostsFiltered_secondRoundFillsPage() {
        UUID ownedByViewer = UUID.randomUUID();
        UUID blockedPost = UUID.randomUUID();
        UUID okPost1 = UUID.randomUUID();
        UUID okPost2 = UUID.randomUUID();
        UUID blockedOwnerId = UUID.randomUUID();
        UUID ok1OwnerId = UUID.randomUUID();
        UUID ok2OwnerId = UUID.randomUUID();
        Post viewerOwned = publishedPost(ownedByViewer, viewerId);
        Post blocked = publishedPost(blockedPost, blockedOwnerId);
        Post ok1 = publishedPost(okPost1, ok1OwnerId);
        Post ok2 = publishedPost(okPost2, ok2OwnerId);

        // First round: 3 candidates, but only ok1 survives (owned + blocked filtered) - page of 2
        // is not yet full, so a second round must run.
        when(recommendationSource.fetch(eq(viewerId), eq('g'), eq(4), eq(0)))
                .thenReturn(
                        new SourceBatch(
                                'g',
                                List.of(
                                        new GorseScore(ownedByViewer.toString(), 9.0),
                                        new GorseScore(blockedPost.toString(), 8.0),
                                        new GorseScore(okPost1.toString(), 7.0))));
        when(postLookupService.findActiveByIds(List.of(ownedByViewer, blockedPost, okPost1)))
                .thenReturn(List.of(viewerOwned, blocked, ok1));
        // The batch visibility check runs once for the round's distinct owner set; blockedOwnerId
        // is excluded from the visible result, ok1OwnerId (and the viewer's own id) is included.
        when(postVisibilityService.filterVisibleOwnerIds(
                        viewerId, Set.of(viewerId, blockedOwnerId, ok1OwnerId)))
                .thenReturn(Set.of(viewerId, ok1OwnerId));

        // Second round starts at offset 3 (all 3 raw candidates from round 1 were consumed).
        when(recommendationSource.fetch(eq(viewerId), eq('g'), eq(4), eq(3)))
                .thenReturn(new SourceBatch('g', List.of(new GorseScore(okPost2.toString(), 6.0))));
        when(postLookupService.findActiveByIds(List.of(okPost2))).thenReturn(List.of(ok2));
        when(postVisibilityService.filterVisibleOwnerIds(viewerId, Set.of(ok2OwnerId)))
                .thenReturn(Set.of(ok2OwnerId));

        CursorPageResponse<FeedPostResponse> page = service.getRecommendedFeed(viewerId, null, 2);

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).id()).isEqualTo(okPost1);
        assertThat(page.getContent().get(1).id()).isEqualTo(okPost2);
        verify(recommendationSource).fetch(viewerId, 'g', 4, 0);
        verify(recommendationSource).fetch(viewerId, 'g', 4, 3);
    }

    @Test
    void getRecommendedFeed_bothSourcesEmptyOnFirstPage_fallsBackToChronologicalFeed() {
        when(recommendationSource.fetch(eq(viewerId), eq('g'), eq(10), eq(0)))
                .thenReturn(new SourceBatch('g', List.of()));
        CursorPageResponse<FeedPostResponse> chronoPage =
                CursorPageResponse.of(
                        List.of(feedResponse(UUID.randomUUID())), false, "a", "b", false);
        when(postService.getFeed(viewerId, null, 5)).thenReturn(chronoPage);

        CursorPageResponse<FeedPostResponse> page = service.getRecommendedFeed(viewerId, null, 5);

        assertThat(page).isSameAs(chronoPage);
        verify(postLookupService, never()).findActiveByIds(anyList());
    }

    @Test
    void getRecommendedFeed_sourceDegradesToPopular_cursorReflectsPopularSource() {
        UUID postId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Post post = publishedPost(postId, ownerId);
        // Gorse degraded mid-round-trip: RecommendationSource itself flips the tagged source.
        when(recommendationSource.fetch(eq(viewerId), eq('g'), eq(2), eq(0)))
                .thenReturn(new SourceBatch('p', List.of(new GorseScore(postId.toString(), null))));
        when(postLookupService.findActiveByIds(anyList())).thenReturn(List.of(post));
        when(postVisibilityService.filterVisibleOwnerIds(viewerId, Set.of(ownerId)))
                .thenReturn(Set.of(ownerId));

        CursorPageResponse<FeedPostResponse> page = service.getRecommendedFeed(viewerId, null, 1);

        assertThat(page.getContent()).hasSize(1);
        assertThat(decodeCursor(page.getPageInfo().getEndCursor())).startsWith("p:");
    }

    @Test
    void getRecommendedFeed_gorseReturnsNoScore_rankingScoreIsNull() {
        UUID postId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Post post = publishedPost(postId, ownerId);
        when(recommendationSource.fetch(eq(viewerId), eq('g'), eq(2), eq(0)))
                .thenReturn(new SourceBatch('g', List.of(new GorseScore(postId.toString(), null))));
        when(postLookupService.findActiveByIds(anyList())).thenReturn(List.of(post));
        when(postVisibilityService.filterVisibleOwnerIds(viewerId, Set.of(ownerId)))
                .thenReturn(Set.of(ownerId));

        CursorPageResponse<FeedPostResponse> page = service.getRecommendedFeed(viewerId, null, 1);

        assertThat(page.getContent().get(0).rankingScore()).isNull();
    }

    @Test
    void getRecommendedFeed_previousPagePopularCursor_continuesFromPopularSource() {
        String popularCursor = Base64.getEncoder().encodeToString("p:7".getBytes());
        when(recommendationSource.fetch(eq(viewerId), eq('p'), eq(10), eq(7)))
                .thenReturn(new SourceBatch('p', List.of()));

        // Not the first page (offset 7 > 0), so an empty result returns as an empty ranked page
        // rather than restarting the chronological feed from page one.
        CursorPageResponse<FeedPostResponse> page =
                service.getRecommendedFeed(viewerId, popularCursor, 5);

        assertThat(page.getContent()).isEmpty();
        verify(postService, never()).getFeed(any(), any(), anyInt());
    }
}
