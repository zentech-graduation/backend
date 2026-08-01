package com.app.modules.recommendation.service.impl;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.app.common.response.CursorPageResponse;
import com.app.modules.post.dto.response.FeedPostResponse;
import com.app.modules.post.entity.Post;
import com.app.modules.post.enums.PostStatus;
import com.app.modules.post.service.PostLookupService;
import com.app.modules.post.service.PostService;
import com.app.modules.post.service.PostVisibilityService;
import com.app.modules.recommendation.client.dto.GorseScore;
import com.app.modules.recommendation.config.GorseProperties;
import com.app.modules.recommendation.service.RecommendationFeedService;
import com.app.modules.recommendation.service.impl.feed.RecommendationSource;
import com.app.modules.recommendation.service.impl.feed.RecommendationSource.SourceBatch;

@Service
public class RecommendationFeedServiceImpl implements RecommendationFeedService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    // Bounds forged cursors; also matches the depth at which Gorse's cached list is exhausted
    private static final int MAX_OFFSET = 10_000;
    // Bounds source round trips per page so a heavily-filtered candidate stream cannot stall the
    // request; the page is simply served short instead.
    private static final int MAX_SOURCE_ROUNDS = 5;

    private final RecommendationSource recommendationSource;
    private final PostLookupService postLookupService;
    private final PostVisibilityService postVisibilityService;
    private final PostService postService;
    private final GorseProperties gorseProperties;

    public RecommendationFeedServiceImpl(
            RecommendationSource recommendationSource,
            PostLookupService postLookupService,
            PostVisibilityService postVisibilityService,
            PostService postService,
            GorseProperties gorseProperties) {
        this.recommendationSource = recommendationSource;
        this.postLookupService = postLookupService;
        this.postVisibilityService = postVisibilityService;
        this.postService = postService;
        this.gorseProperties = gorseProperties;
    }

    @Override
    public CursorPageResponse<FeedPostResponse> getRecommendedFeed(
            UUID viewerId, String cursor, int limit) {
        int pageSize = normalizeLimit(limit);
        FeedCursor decoded = FeedCursor.decode(cursor);
        if (decoded == null) {
            // Not a ranked cursor: the previous page came from the chronological fallback, whose
            // timestamp cursors the post module owns. Delegate so pagination continues seamlessly.
            return postService.getFeed(viewerId, cursor, pageSize);
        }

        char source = decoded.source();
        int offset = decoded.offset();
        int rawConsumed = 0;
        List<FeedPostResponse> content = new ArrayList<>(pageSize);
        Set<UUID> seenPostIds = new HashSet<>();
        int fetchSize = Math.max(pageSize * gorseProperties.getRecommendMultiplier(), pageSize);

        for (int round = 0; round < MAX_SOURCE_ROUNDS && content.size() < pageSize; round++) {
            SourceBatch batch =
                    recommendationSource.fetch(viewerId, source, fetchSize, offset + rawConsumed);
            // A degraded batch flips the cursor source so later pages keep reading the same list
            source = batch.source();
            List<GorseScore> scores = batch.scores();
            if (scores.isEmpty()) {
                break;
            }
            rawConsumed += appendVisible(viewerId, scores, content, seenPostIds, pageSize);
        }

        if (content.isEmpty() && decoded.isFirstPage()) {
            // Both ranked sources empty or unavailable: serve the chronological following feed
            // (its own cursor scheme takes over on subsequent pages).
            return postService.getFeed(viewerId, null, pageSize);
        }
        String startCursor = content.isEmpty() ? null : FeedCursor.encode(source, offset);
        String endCursor =
                content.isEmpty() ? null : FeedCursor.encode(source, offset + rawConsumed);
        return CursorPageResponse.of(
                content, pageSize, startCursor, endCursor, decoded.offset() > 0);
    }

    /**
     * Hydrates a candidate batch, filters it down to posts the viewer may see, and appends up to
     * the page capacity. Returns how many raw candidates were consumed (accepted or rejected);
     * candidates beyond a filled page are not counted so the next page re-reads them.
     */
    private int appendVisible(
            UUID viewerId,
            List<GorseScore> scores,
            List<FeedPostResponse> content,
            Set<UUID> seenPostIds,
            int pageSize) {
        Map<UUID, Post> postsById =
                postLookupService.findActiveByIds(parseIds(scores)).stream()
                        .collect(Collectors.toMap(Post::getId, Function.identity()));
        List<Post> accepted = new ArrayList<>();
        List<Double> acceptedScores = new ArrayList<>();
        int consumed = 0;
        for (GorseScore score : scores) {
            consumed++;
            UUID postId = tryParseUuid(score.id());
            if (postId == null || !seenPostIds.add(postId)) {
                continue;
            }
            Post post = postsById.get(postId);
            if (post == null
                    || post.getStatus() != PostStatus.PUBLISHED
                    || viewerId.equals(post.getUserId())
                    || !postVisibilityService.isVisibleTo(viewerId, post)) {
                continue;
            }
            accepted.add(post);
            acceptedScores.add(score.score());
            if (content.size() + accepted.size() >= pageSize) {
                break;
            }
        }
        if (!accepted.isEmpty()) {
            List<FeedPostResponse> assembled = postLookupService.assembleFeed(accepted);
            for (int i = 0; i < assembled.size(); i++) {
                content.add(assembled.get(i).withRankingScore(acceptedScores.get(i)));
            }
        }
        return consumed;
    }

    private static List<UUID> parseIds(List<GorseScore> scores) {
        List<UUID> ids = new ArrayList<>(scores.size());
        for (GorseScore score : scores) {
            UUID id = tryParseUuid(score.id());
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static UUID tryParseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            // Foreign ids (e.g. manually inserted test items) are skipped rather than failing the
            // whole page.
            return null;
        }
    }

    private static int normalizeLimit(int limit) {
        return limit > MAX_PAGE_SIZE ? MAX_PAGE_SIZE : (limit < 1 ? DEFAULT_PAGE_SIZE : limit);
    }

    /**
     * Opaque ranked-feed cursor: base64 of {@code <source>:<offset>} where source is 'g' (Gorse
     * recommend) or 'p' (popularity fallback). {@code decode} returns null for any other cursor
     * shape, which the service treats as a chronological-feed cursor to delegate.
     */
    record FeedCursor(char source, int offset) {

        boolean isFirstPage() {
            return offset == 0;
        }

        static String encode(char source, int offset) {
            return Base64.getEncoder()
                    .encodeToString((source + ":" + offset).getBytes(StandardCharsets.UTF_8));
        }

        static FeedCursor decode(String cursor) {
            if (cursor == null || cursor.isBlank()) {
                return new FeedCursor(RecommendationSource.SOURCE_GORSE, 0);
            }
            String raw;
            try {
                raw = new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return null;
            }
            if (raw.length() < 3
                    || raw.charAt(1) != ':'
                    || (raw.charAt(0) != RecommendationSource.SOURCE_GORSE
                            && raw.charAt(0) != RecommendationSource.SOURCE_POPULAR)) {
                return null;
            }
            try {
                int offset = Integer.parseInt(raw.substring(2));
                if (offset < 0 || offset > MAX_OFFSET) {
                    return new FeedCursor(raw.charAt(0), 0);
                }
                return new FeedCursor(raw.charAt(0), offset);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
