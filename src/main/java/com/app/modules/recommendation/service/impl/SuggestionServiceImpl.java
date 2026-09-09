package com.app.modules.recommendation.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.response.UserListItemResponse;
import com.app.common.response.UserSummaryResponse;
import com.app.common.response.ViewerRelationshipResponse;
import com.app.modules.recommendation.repository.SuggestionDismissalRepository;
import com.app.modules.recommendation.repository.UserSuggestionRepository;
import com.app.modules.recommendation.service.SuggestionService;
import com.app.modules.social.service.SocialService;
import com.app.modules.users.service.UserSummaryService;

@Service
public class SuggestionServiceImpl implements SuggestionService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionServiceImpl.class);

    /**
     * Reciprocal-rank-fusion damping constant.
     *
     * <p>60, the value the original RRF paper settled on and the one the personalised trending
     * blend already uses here. A much smaller constant makes rank 1 worth roughly twice rank 2,
     * which lets whichever source happens to rank an account first dominate the fused order
     * outright.
     */
    private static final int RRF_K = 60;

    /**
     * Weights on each source's rank contribution.
     *
     * <p>The follow graph leads because a shared follow is the strongest and most explainable
     * signal available. Gorse and affinity are equal behind it: both are behavioural, both are
     * noisier, and neither has earned a claim to outrank the other on this data.
     *
     * <p>Fusion is on rank rather than on the three scores directly, for the reason the trending
     * blend gives: a shared-follower count, a Gorse similarity and a histogram intersection are in
     * three different units, and adding them would rank by whichever happens to have the widest
     * numeric range rather than by relevance.
     */
    private static final double GRAPH_WEIGHT = 1.0;

    private static final double GORSE_WEIGHT = 0.6;
    private static final double AFFINITY_WEIGHT = 0.6;

    /** How deep into each source list the fusion looks. */
    private static final int SOURCE_DEPTH = 60;

    /**
     * How many of the viewer's hashtags the affinity source matches on.
     *
     * <p>Bounds the self-join's fan-out. The affinity profile's tail is near-zero scores that
     * contribute almost nothing to the overlap sum while carrying most of the join width, so
     * cutting it costs little ranking signal and removes an O(viewers x table size) shape.
     */
    private static final int AFFINITY_PROFILE_DEPTH = 32;

    /** How many rows one account's precomputed list holds. */
    private static final int STORED_LIST_SIZE = 50;

    private static final int MAX_PAGE_SIZE = 50;

    private final UserSuggestionRepository userSuggestionRepository;
    private final SuggestionDismissalRepository suggestionDismissalRepository;
    private final GorseNeighbourSource gorseNeighbourSource;
    private final UserSummaryService userSummaryService;
    private final SocialService socialService;

    public SuggestionServiceImpl(
            UserSuggestionRepository userSuggestionRepository,
            SuggestionDismissalRepository suggestionDismissalRepository,
            GorseNeighbourSource gorseNeighbourSource,
            UserSummaryService userSummaryService,
            SocialService socialService) {
        this.userSuggestionRepository = userSuggestionRepository;
        this.suggestionDismissalRepository = suggestionDismissalRepository;
        this.gorseNeighbourSource = gorseNeighbourSource;
        this.userSummaryService = userSummaryService;
        this.socialService = socialService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserListItemResponse> suggestionsFor(UUID viewerId, int limit) {
        int size = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        List<UUID> ids = userSuggestionRepository.findVisibleSuggestions(viewerId, size);
        if (ids.isEmpty()) {
            // Cold start, and also the case where a whole precomputed list has since been followed
            // or dismissed. Both look identical to the reader: there is nothing personalised yet.
            ids = userSuggestionRepository.findVerifiedColdStart(viewerId, size);
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, UserSummaryResponse> summaries = userSummaryService.loadSummaries(ids);
        Map<UUID, ViewerRelationshipResponse> relationships =
                socialService.loadRelationships(viewerId, ids);
        List<UserListItemResponse> rows = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            rows.add(
                    new UserListItemResponse(
                            summaries.get(id),
                            relationships.getOrDefault(id, ViewerRelationshipResponse.NONE)));
        }
        return rows;
    }

    @Override
    @Transactional
    public void dismiss(UUID viewerId, UUID dismissedId) {
        // Self-dismissal is refused by the table's own CHECK constraint, so it cannot be written
        // even if a caller asks for it; returning early keeps that from surfacing as a 500.
        if (viewerId.equals(dismissedId)) {
            return;
        }
        suggestionDismissalRepository.dismiss(viewerId, dismissedId);
    }

    @Override
    @Transactional
    public int rebuildFor(UUID viewerId) {
        // One clock domain for the whole run. This value stamps every row the run writes and
        // bounds the sweep that follows, so the sweep cannot delete the generation it just wrote.
        // Reading it from the JVM while the insert used the database's NOW() is what previously
        // wiped the table whenever the database clock trailed the JVM by more than the statement
        // round trip.
        OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC);

        List<UUID> graph = userSuggestionRepository.findTwoHopCandidates(viewerId, SOURCE_DEPTH);
        List<UUID> gorse = gorseNeighbourSource.neighbours(viewerId, SOURCE_DEPTH);
        List<UUID> affinity =
                userSuggestionRepository.findAffinityCandidates(
                        viewerId, AFFINITY_PROFILE_DEPTH, SOURCE_DEPTH);

        Map<UUID, Double> fused = new HashMap<>();
        Map<UUID, Set<String>> sources = new HashMap<>();
        contribute(fused, sources, graph, GRAPH_WEIGHT, "graph");
        contribute(fused, sources, gorse, GORSE_WEIGHT, "gorse");
        contribute(fused, sources, affinity, AFFINITY_WEIGHT, "affinity");

        if (fused.isEmpty()) {
            // Nothing to store. The read path answers from the verified cold-start list instead,
            // which is deliberately not materialised here: it is the same list for everyone and
            // storing a copy per account would be fifty rows each to say one thing.
            userSuggestionRepository.deleteStaleFor(viewerId, startedAt);
            return 0;
        }

        List<UUID> ordered =
                fused.entrySet().stream()
                        .sorted(
                                Map.Entry.<UUID, Double>comparingByValue()
                                        .reversed()
                                        // Total order, so two accounts with an identical fused
                                        // score do not swap places between runs and make the list
                                        // look unstable for no reason.
                                        .thenComparing(Map.Entry::getKey))
                        .limit(STORED_LIST_SIZE)
                        .map(Map.Entry::getKey)
                        .toList();

        short rank = 1;
        for (UUID candidate : ordered) {
            userSuggestionRepository.upsertSuggestion(
                    viewerId,
                    candidate,
                    rank,
                    BigDecimal.valueOf(fused.get(candidate)).setScale(8, RoundingMode.HALF_UP),
                    String.join(",", new TreeSet<>(sources.get(candidate))),
                    startedAt);
            rank++;
        }
        // Rows this run did not rewrite are the previous generation and no longer qualify.
        userSuggestionRepository.deleteStaleFor(viewerId, startedAt);
        // Counted after the sweep, not before: reporting ordered.size() here is what let a sweep
        // that deleted its own generation still log a healthy row count.
        return userSuggestionRepository.countFreshFor(viewerId, startedAt);
    }

    private static void contribute(
            Map<UUID, Double> fused,
            Map<UUID, Set<String>> sources,
            List<UUID> ranked,
            double weight,
            String label) {
        for (int i = 0; i < ranked.size(); i++) {
            UUID id = ranked.get(i);
            fused.merge(id, weight / (RRF_K + i + 1.0), Double::sum);
            sources.computeIfAbsent(id, key -> new LinkedHashSet<>()).add(label);
        }
    }
}
