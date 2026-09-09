package com.app.modules.hashtag.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.app.common.response.PageResponse;
import com.app.modules.hashtag.dto.response.HashtagTrendingResponse;
import com.app.modules.hashtag.enums.TrendingSource;
import com.app.modules.hashtag.service.HashtagTrendingService;
import com.app.modules.recommendation.entity.UserHashtagAffinity;
import com.app.modules.recommendation.entity.UserHashtagAffinityId;
import com.app.modules.recommendation.repository.UserHashtagAffinityRepository;
import com.app.modules.recommendation.service.HashtagAffinityService;

@ExtendWith(MockitoExtension.class)
class PersonalisedTrendingServiceImplTest {

    private static final UUID VIEWER_ID = UUID.randomUUID();

    @Mock private HashtagTrendingService hashtagTrendingService;
    @Mock private HashtagAffinityService hashtagAffinityService;
    @Mock private UserHashtagAffinityRepository affinityRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private PersonalisedTrendingServiceImpl service;

    @BeforeEach
    void setUp() {
        // Cache always misses here, so every assertion is against the computed list rather than a
        // serialized round trip.
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(valueOps.get(anyString())).thenReturn(null);
        service =
                new PersonalisedTrendingServiceImpl(
                        hashtagTrendingService,
                        hashtagAffinityService,
                        affinityRepository,
                        redisTemplate);
    }

    private static HashtagTrendingResponse platformEntry(UUID id, String name, int rank) {
        return new HashtagTrendingResponse(
                id, name, 10, rank, null, null, false, TrendingSource.PLATFORM);
    }

    private static UserHashtagAffinity affinity(UUID hashtagId) {
        return UserHashtagAffinity.builder()
                .id(new UserHashtagAffinityId(VIEWER_ID, hashtagId))
                .build();
    }

    private void platform(List<HashtagTrendingResponse> entries) {
        when(hashtagTrendingService.getTrending(any()))
                .thenReturn(
                        PageResponse.from(
                                new PageImpl<>(entries, PageRequest.of(0, 50), entries.size())));
    }

    // Cold start is the normal state for a new account, not an error. The client must never be
    // handed an empty personalised tab, so the platform list is served silently.
    @Test
    void getPersonalisedTrending_noAffinity_fallsBackToPlatformList() {
        platform(
                List.of(
                        platformEntry(UUID.randomUUID(), "goldprice", 1),
                        platformEntry(UUID.randomUUID(), "shoponline", 2)));
        when(hashtagAffinityService.findTopForUser(VIEWER_ID, 50)).thenReturn(List.of());

        PageResponse<HashtagTrendingResponse> result =
                service.getPersonalisedTrending(VIEWER_ID, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).name()).isEqualTo("goldprice");
        // No adjacency query is issued for a user with nothing to be adjacent to.
        verifyNoInteractions(affinityRepository);
    }

    // The point of fusing on rank: a hashtag the caller cares about climbs above a platform-popular
    // one it sits below on the platform list.
    @Test
    void getPersonalisedTrending_affinityHashtag_outranksPlatformOnlyHashtag() {
        UUID popular = UUID.randomUUID();
        UUID mine = UUID.randomUUID();
        platform(
                List.of(platformEntry(popular, "goldprice", 1), platformEntry(mine, "reactjs", 2)));
        when(hashtagAffinityService.findTopForUser(VIEWER_ID, 50))
                .thenReturn(List.of(affinity(mine)));
        when(affinityRepository.findAdjacentHashtagIds(VIEWER_ID, 50)).thenReturn(List.of());

        PageResponse<HashtagTrendingResponse> result =
                service.getPersonalisedTrending(VIEWER_ID, PageRequest.of(0, 10));

        assertThat(result.getContent().get(0).hashtagId()).isEqualTo(mine);
        assertThat(result.getContent().get(0).source()).isEqualTo(TrendingSource.AFFINITY);
    }

    // Reserved novelty slots must actually be filled from the adjacency pool, or the list collapses
    // into what the caller already reads.
    @Test
    void getPersonalisedTrending_reservesSlotsForAdjacentHashtags() {
        List<HashtagTrendingResponse> platformList = new ArrayList<>();
        List<UserHashtagAffinity> affinities = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            UUID id = UUID.randomUUID();
            platformList.add(platformEntry(id, "known" + i, i + 1));
            affinities.add(affinity(id));
        }
        UUID adjacentId = UUID.randomUUID();
        platform(platformList);
        when(hashtagAffinityService.findTopForUser(VIEWER_ID, 50)).thenReturn(affinities);
        when(affinityRepository.findAdjacentHashtagIds(VIEWER_ID, 50))
                .thenReturn(List.of(adjacentId));
        when(hashtagTrendingService.describeHashtags(any()))
                .thenReturn(
                        List.of(
                                new HashtagTrendingResponse(
                                        adjacentId,
                                        "newthing",
                                        3,
                                        0,
                                        null,
                                        null,
                                        false,
                                        TrendingSource.PLATFORM)));

        PageResponse<HashtagTrendingResponse> result =
                service.getPersonalisedTrending(VIEWER_ID, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(HashtagTrendingResponse::hashtagId)
                .contains(adjacentId);
        assertThat(result.getContent())
                .filteredOn(e -> e.source() == TrendingSource.NOVEL)
                .isNotEmpty();
    }

    // Reserving novelty slots must never shorten the page. A caller who engages with nearly every
    // hashtag has almost no adjacency pool, and a full page is still the correct outcome.
    @Test
    void getPersonalisedTrending_thinAdjacencyPool_stillFillsThePage() {
        List<HashtagTrendingResponse> platformList = new ArrayList<>();
        List<UserHashtagAffinity> affinities = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            UUID id = UUID.randomUUID();
            platformList.add(platformEntry(id, "tag" + i, i + 1));
            affinities.add(affinity(id));
        }
        platform(platformList);
        when(hashtagAffinityService.findTopForUser(VIEWER_ID, 50)).thenReturn(affinities);
        when(affinityRepository.findAdjacentHashtagIds(VIEWER_ID, 50)).thenReturn(List.of());

        PageResponse<HashtagTrendingResponse> result =
                service.getPersonalisedTrending(VIEWER_ID, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(10);
    }

    // A platform-wide pin is platform-wide: it leads the personalised list too.
    @Test
    void getPersonalisedTrending_pinnedHashtag_leadsTheList() {
        UUID pinned = UUID.randomUUID();
        UUID mine = UUID.randomUUID();
        platform(
                List.of(
                        new HashtagTrendingResponse(
                                pinned,
                                "featured",
                                5,
                                9,
                                null,
                                null,
                                true,
                                TrendingSource.PLATFORM),
                        platformEntry(mine, "reactjs", 1)));
        when(hashtagAffinityService.findTopForUser(VIEWER_ID, 50))
                .thenReturn(List.of(affinity(mine)));
        when(affinityRepository.findAdjacentHashtagIds(VIEWER_ID, 50)).thenReturn(List.of());

        PageResponse<HashtagTrendingResponse> result =
                service.getPersonalisedTrending(VIEWER_ID, PageRequest.of(0, 10));

        assertThat(result.getContent().get(0).hashtagId()).isEqualTo(pinned);
    }

    // Redis is a cache tier: its being unavailable degrades latency, never correctness.
    @Test
    void getPersonalisedTrending_cacheReadFails_stillComputesTheList() {
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));
        platform(List.of(platformEntry(UUID.randomUUID(), "goldprice", 1)));
        when(hashtagAffinityService.findTopForUser(VIEWER_ID, 50)).thenReturn(List.of());

        PageResponse<HashtagTrendingResponse> result =
                service.getPersonalisedTrending(VIEWER_ID, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
    }
}
