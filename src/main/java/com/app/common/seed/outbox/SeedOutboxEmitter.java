package com.app.common.seed.outbox;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Emits a bounded set of real domain events through the transactional outbox for every row {@link
 * PostSeedWriter}, {@link CommentSeedWriter} and {@link EngagementSeedWriter} have already written,
 * so the real Elasticsearch and Gorse consumers can index and learn from the seeded database.
 *
 * <p>Reads every id it needs back from the database rather than accepting id collections as
 * parameters: {@link PostSeedWriter#write} only returns its seed-id-to-post-id map (hashtag ids
 * stay internal), and {@link EngagementSeedWriter#write} returns {@code void}. Reading rows back
 * after every domain writer has run is the same pattern {@link CommentSeedWriter} and {@link
 * EngagementSeedWriter} already use to resolve state a prior writer did not expose.
 *
 * <p><b>{@code post.viewed.v1} is never emitted</b> - {@code posts.view_count} was already written
 * directly by {@link PostSeedWriter}, and view activity already landed in {@code user_events}
 * directly via {@link AnalyticsSeedWriter}. No view events go through the broker.
 *
 * <p>Enqueuing happens through the real {@code OutboxService} bean (wrapped in {@link
 * SeedOutboxBatchWriter}'s own {@code @Transactional} methods), which guarantees the payload always
 * matches {@code DomainEventEnvelopeJson}'s exact shape because it is the same production code path
 * every real write uses.
 */
@Slf4j
@Service
@Profile("dev")
@RequiredArgsConstructor
public class SeedOutboxEmitter {

    // A few hundred enqueue calls per transaction, not one transaction for the whole ~21,000+ event
    // volume, so the emission phase never holds locks for its full duration.
    private static final int BATCH_SIZE = 300;

    private static final String PUBLISHED_STATUS_FILTER =
            "SELECT id, user_id, created_at FROM posts WHERE status = 'published'::post_status";

    private final JdbcTemplate jdbc;
    private final SeedOutboxBatchWriter batchWriter;

    /**
     * One published post's identifying data needed to build a {@code post.index.upsert.v1} payload.
     */
    public record PostIndexRow(
            UUID postId, UUID userId, List<String> hashtagIds, Instant createdAt) {}

    /** One {@code post_likes}/{@code post_saves} row's identifying data. */
    public record ReactionRow(UUID postId, UUID postOwnerId, UUID userId) {}

    /** One non-soft-deleted comment row's identifying data. */
    public record CommentRow(
            UUID commentId,
            UUID postId,
            UUID postOwnerId,
            UUID userId,
            int depth,
            UUID parentOwnerId) {}

    /** One real sample of each of the five emitted event kinds, used by the envelope proof. */
    public record ProofSample(
            PostIndexRow post,
            UUID hashtagId,
            ReactionRow like,
            ReactionRow save,
            CommentRow comment) {}

    /** Per-event-type counts of what {@link #emitFullVolume()} actually enqueued. */
    public record EmissionCounts(
            int postIndex, int hashtagIndex, int likes, int saves, int comments) {
        public int total() {
            return postIndex + hashtagIndex + likes + saves + comments;
        }
    }

    /**
     * Emits exactly one real event of each of the five seed-emitted event types, sampled from
     * whatever the database currently holds.
     *
     * <p>This is the mandatory envelope-correctness gate: only after these five events drain
     * cleanly through the real outbox publisher and real consumers should {@link #emitFullVolume()}
     * run.
     *
     * @return the sample rows the proof slice was built from, for assertions and logging
     * @throws IllegalStateException if the database does not yet contain at least one row of every
     *     required kind - every domain writer must run before this method is called
     */
    public ProofSample emitProofSlice() {
        List<PostIndexRow> posts = fetchPublishedPosts();
        requireNonEmpty(posts, "published posts");
        List<UUID> hashtagIds = fetchAllHashtagIds();
        requireNonEmpty(hashtagIds, "hashtags");
        List<ReactionRow> likes = fetchPostLikes();
        requireNonEmpty(likes, "post_likes rows");
        List<ReactionRow> saves = fetchPostSaves();
        requireNonEmpty(saves, "post_saves rows");
        List<CommentRow> comments = fetchComments();
        requireNonEmpty(comments, "comments");

        ProofSample sample =
                new ProofSample(
                        posts.get(0),
                        hashtagIds.get(0),
                        likes.get(0),
                        saves.get(0),
                        comments.get(0));
        batchWriter.emitProof(sample);
        log.info(
                "[seed] outbox proof slice emitted: post={}, hashtag={}, like.post={}, save.post={},"
                        + " comment={}",
                sample.post().postId(),
                sample.hashtagId(),
                sample.like().postId(),
                sample.save().postId(),
                sample.comment().commentId());
        return sample;
    }

    /**
     * Emits the full seed event volume through the real transactional outbox: one {@code
     * post.index.upsert.v1} per published post, one {@code hashtag.index.upsert.v1} per hashtag
     * row, one {@code post.liked.v1} per {@code post_likes} row, one {@code post.saved.v1} per
     * {@code post_saves} row, and one {@code comment.created.v1} per non-soft-deleted comment.
     *
     * @return the number of events enqueued per type
     */
    public EmissionCounts emitFullVolume() {
        List<PostIndexRow> posts = fetchPublishedPosts();
        List<UUID> hashtagIds = fetchAllHashtagIds();
        List<ReactionRow> likes = fetchPostLikes();
        List<ReactionRow> saves = fetchPostSaves();
        List<CommentRow> comments = fetchComments();

        emitBatched(posts, batchWriter::emitPostIndexBatch);
        emitBatched(hashtagIds, batchWriter::emitHashtagIndexBatch);
        emitBatched(likes, batchWriter::emitLikeBatch);
        emitBatched(saves, batchWriter::emitSaveBatch);
        emitBatched(comments, batchWriter::emitCommentBatch);

        EmissionCounts counts =
                new EmissionCounts(
                        posts.size(),
                        hashtagIds.size(),
                        likes.size(),
                        saves.size(),
                        comments.size());
        log.info(
                "[seed] outbox full volume emitted: postIndex={}, hashtagIndex={}, likes={}, saves={},"
                        + " comments={}, total={}",
                counts.postIndex(),
                counts.hashtagIndex(),
                counts.likes(),
                counts.saves(),
                counts.comments(),
                counts.total());
        return counts;
    }

    private void requireNonEmpty(List<?> rows, String description) {
        if (rows.isEmpty()) {
            throw new IllegalStateException(
                    "SeedOutboxEmitter: no "
                            + description
                            + " found - every domain writer must run before emitting outbox events");
        }
    }

    // Each batch is handed to SeedOutboxBatchWriter, a separate injected bean: the call below goes
    // through that bean's own Spring proxy, so its @Transactional per-batch methods actually open a
    // new transaction per batch. Calling a @Transactional method on `this` instead would bypass the
    // proxy (self-invocation) and silently run with no transaction at all.
    private <T> void emitBatched(List<T> items, Consumer<List<T>> emitBatch) {
        for (int start = 0; start < items.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, items.size());
            emitBatch.accept(items.subList(start, end));
        }
    }

    private List<PostIndexRow> fetchPublishedPosts() {
        Map<UUID, List<String>> hashtagIdsByPostId = fetchHashtagIdsByPostId();
        return jdbc.query(
                PUBLISHED_STATUS_FILTER,
                (rs, rowNum) -> {
                    UUID postId = (UUID) rs.getObject("id");
                    return new PostIndexRow(
                            postId,
                            (UUID) rs.getObject("user_id"),
                            hashtagIdsByPostId.getOrDefault(postId, List.of()),
                            rs.getTimestamp("created_at").toInstant());
                });
    }

    private Map<UUID, List<String>> fetchHashtagIdsByPostId() {
        Map<UUID, List<String>> result = new HashMap<>();
        jdbc.query(
                "SELECT post_id, hashtag_id FROM post_hashtags",
                rs -> {
                    UUID postId = (UUID) rs.getObject("post_id");
                    String hashtagId = rs.getObject("hashtag_id").toString();
                    result.computeIfAbsent(postId, key -> new ArrayList<>()).add(hashtagId);
                });
        return result;
    }

    private List<UUID> fetchAllHashtagIds() {
        return jdbc.query("SELECT id FROM hashtags", (rs, rowNum) -> (UUID) rs.getObject("id"));
    }

    private List<ReactionRow> fetchPostLikes() {
        return fetchReactions(
                "SELECT pl.post_id, pl.user_id, p.user_id AS post_owner_id FROM post_likes pl JOIN"
                        + " posts p ON p.id = pl.post_id");
    }

    private List<ReactionRow> fetchPostSaves() {
        return fetchReactions(
                "SELECT ps.post_id, ps.user_id, p.user_id AS post_owner_id FROM post_saves ps JOIN"
                        + " posts p ON p.id = ps.post_id");
    }

    private List<ReactionRow> fetchReactions(String sql) {
        return jdbc.query(
                sql,
                (rs, rowNum) ->
                        new ReactionRow(
                                (UUID) rs.getObject("post_id"),
                                (UUID) rs.getObject("post_owner_id"),
                                (UUID) rs.getObject("user_id")));
    }

    private List<CommentRow> fetchComments() {
        String sql =
                "SELECT c.id, c.post_id, c.user_id, c.depth, p.user_id AS post_owner_id, parent.user_id"
                        + " AS parent_owner_id FROM comments c JOIN posts p ON p.id = c.post_id LEFT"
                        + " JOIN comments parent ON parent.id = c.parent_id WHERE c.deleted_at IS"
                        + " NULL";
        return jdbc.query(
                sql,
                (rs, rowNum) -> {
                    Object parentOwner = rs.getObject("parent_owner_id");
                    return new CommentRow(
                            (UUID) rs.getObject("id"),
                            (UUID) rs.getObject("post_id"),
                            (UUID) rs.getObject("post_owner_id"),
                            (UUID) rs.getObject("user_id"),
                            rs.getInt("depth"),
                            parentOwner == null ? null : (UUID) parentOwner);
                });
    }
}
