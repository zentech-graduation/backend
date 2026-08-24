package com.app.common.seed.writer;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.app.common.seed.time.SeedTimeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seeds {@code notifications} (~{@value #TARGET_NOTIFICATION_COUNT} from the follow/comment/story
 * pool plus dedicated shares for likes, messages and mentions, ~30% unread) by reading back every
 * event an earlier writer already persisted that would, on the production write path, have fanned
 * out through a RabbitMQ consumer into a notification row - {@link SocialGraphSeedWriter}'s {@code
 * follows}, {@link CommentSeedWriter}'s {@code comments}, {@link StorySeedWriter}'s {@code
 * story_views}, {@link ModerationSeedWriter}'s {@code user_warnings}, {@link
 * EngagementSeedWriter}'s {@code post_likes}/{@code comment_likes}, and {@link MessageSeedWriter}'s
 * {@code messages}.
 *
 * <p>Those consumers ({@code SocialNotificationConsumer}, {@code CommentNotificationConsumer},
 * {@code StoryNotificationConsumer}, {@code AdminNotificationConsumer}, {@code
 * MessageNotificationConsumer}) are held off during a seed run (a later task's concern), so this
 * writer stands in for all five, writing directly with historically correct timestamps instead of
 * round-tripping through the outbox and RabbitMQ. Entity shapes mirror each consumer's own
 * production call exactly: {@code LIKE_POST}/{@code MENTION_POST} carry {@code entityType="post"},
 * {@code entityId}=the post id and a null {@code postId} ({@link
 * com.app.modules.notification.service.NotificationService#create}'s own Javadoc documents this);
 * {@code LIKE_COMMENT}/{@code MENTION_COMMENT} carry {@code entityType="comment"} plus the owning
 * post's id, matching {@code CommentNotificationConsumer}; {@code MESSAGE} carries {@code
 * entityType="message"} and a null {@code postId}, matching {@code MessageNotificationConsumer}.
 *
 * <p><b>Mentions have no real source</b>: no seed content encodes a literal {@code @username} in a
 * caption or comment body, so {@code MENTION_POST}/{@code MENTION_COMMENT} cannot be read back the
 * way every other type is - {@link #fetchMentionPostCandidates} and {@link
 * #fetchMentionCommentCandidates} instead pair a small fixed-size random sample of existing
 * posts/comments with a random different user as the "mentioned" recipient, kept unsampled below
 * for the same reason warning candidates are: too small a population to safely clear {@link
 * SeedRunner}'s per-value enum-coverage floor if it went through {@link #downsample}.
 *
 * <p><b>{@code created_at}</b>: {@code notifications.created_at} is {@code insertable = false} on
 * the JPA entity, an application-layer restriction that does not apply to a raw SQL {@code INSERT}
 * naming the column explicitly - which is exactly what this writer does, overriding the column's
 * {@code DEFAULT now()} the same way every other seed writer overrides a {@code created_at}
 * default.
 */
@Slf4j
@Service
@Profile("dev")
@RequiredArgsConstructor
public class NotificationSeedWriter {

    // Deliberately separate from SeedTimeline's own Random stream (used only for timestamps): this
    // stream drives the downsampling, the mention synthesis and the read/unread split.
    private static final long NOTIFICATION_RANDOM_SEED = 6_104_887L;

    private static final int TARGET_NOTIFICATION_COUNT = 4_000;
    private static final double UNREAD_PROBABILITY = 0.30;

    // Likes and messages have large, naturally occurring candidate pools (thousands of post_likes
    // and comment_likes, low thousands of messages), so each gets its own fixed downsample budget
    // independent of the original follow/comment/story_view pool - mixing them into that shared
    // pool would dilute its ratio enough to put the smallest original type (follow_request, ~40
    // candidates) at risk of dropping under SeedRunner's 5-row floor.
    private static final int LIKE_POST_SAMPLE_BUDGET = 800;
    private static final int LIKE_COMMENT_SAMPLE_BUDGET = 800;
    private static final int MESSAGE_SAMPLE_BUDGET = 500;
    private static final int MENTION_SAMPLE_SIZE = 30;

    private static final String FOLLOW_ENTITY_TYPE = "follow";
    private static final String COMMENT_ENTITY_TYPE = "comment";
    private static final String STORY_ENTITY_TYPE = "story";
    private static final String POST_ENTITY_TYPE = "post";
    private static final String MESSAGE_ENTITY_TYPE = "message";

    private static final String INSERT_NOTIFICATION_SQL =
            "INSERT INTO notifications (id, recipient_id, actor_id, type, entity_type, entity_id,"
                    + " post_id, is_read, read_at, created_at) VALUES (?, ?, ?, ?::notification_type,"
                    + " ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbc;

    /**
     * Inserts the seeded {@code notifications} rows: the original follow/comment/story_view pool
     * downsampled to roughly {@value #TARGET_NOTIFICATION_COUNT}, dedicated like/message shares,
     * synthesized mentions and every warning, unsampled, ~30% marked unread.
     *
     * <p>Must run after {@link SocialGraphSeedWriter}, {@link CommentSeedWriter}, {@link
     * StorySeedWriter}, {@link ModerationSeedWriter}, {@link EngagementSeedWriter} and {@link
     * MessageSeedWriter} - every candidate notification is read back from tables those writers
     * populate.
     */
    public void write(SeedTimeline timeline) {
        Random random = new Random(NOTIFICATION_RANDOM_SEED);

        List<Candidate> warningCandidates = fetchWarningCandidates();
        List<Candidate> mentionCandidates = new ArrayList<>(fetchMentionPostCandidates(random));
        mentionCandidates.addAll(fetchMentionCommentCandidates(random));

        List<Candidate> sampledCandidates = new ArrayList<>();
        sampledCandidates.addAll(fetchFollowCandidates());
        sampledCandidates.addAll(fetchCommentCandidates());
        sampledCandidates.addAll(fetchStoryViewCandidates());

        int remainingBudget =
                Math.max(
                        TARGET_NOTIFICATION_COUNT
                                - warningCandidates.size()
                                - mentionCandidates.size(),
                        0);
        List<Candidate> downsampled = downsample(sampledCandidates, remainingBudget, random);

        List<Candidate> likePostCandidates =
                downsample(fetchLikePostCandidates(), LIKE_POST_SAMPLE_BUDGET, random);
        List<Candidate> likeCommentCandidates =
                downsample(fetchLikeCommentCandidates(), LIKE_COMMENT_SAMPLE_BUDGET, random);
        List<Candidate> messageCandidates =
                downsample(fetchMessageCandidates(), MESSAGE_SAMPLE_BUDGET, random);

        List<Candidate> all = new ArrayList<>(warningCandidates);
        all.addAll(mentionCandidates);
        all.addAll(downsampled);
        all.addAll(likePostCandidates);
        all.addAll(likeCommentCandidates);
        all.addAll(messageCandidates);

        List<Object[]> rows = new ArrayList<>();
        for (Candidate candidate : all) {
            boolean isRead = random.nextDouble() >= UNREAD_PROBABILITY;
            Instant readAt =
                    isRead ? candidate.createdAt().plusSeconds(1 + random.nextInt(3_600)) : null;
            rows.add(
                    new Object[] {
                        UUID.randomUUID(),
                        candidate.recipientId(),
                        candidate.actorId(),
                        candidate.type(),
                        candidate.entityType(),
                        candidate.entityId(),
                        candidate.postId(),
                        isRead,
                        readAt == null ? null : Timestamp.from(readAt),
                        Timestamp.from(candidate.createdAt())
                    });
        }
        jdbc.batchUpdate(INSERT_NOTIFICATION_SQL, rows, rows.size(), this::bindRow);
        log.info(
                "[seed] notifications: {} rows written ({} warning, {} mention, {} sampled from"
                        + " {} candidates, {} like_post, {} like_comment, {} message)",
                rows.size(),
                warningCandidates.size(),
                mentionCandidates.size(),
                downsampled.size(),
                sampledCandidates.size(),
                likePostCandidates.size(),
                likeCommentCandidates.size(),
                messageCandidates.size());
    }

    // Reservoir-style uniform sample without replacement: shuffling the whole candidate list and
    // truncating gives every candidate an equal chance of surviving the downsample, regardless of
    // which event type produced it.
    private List<Candidate> downsample(List<Candidate> candidates, int budget, Random random) {
        if (candidates.size() <= budget) {
            return candidates;
        }
        List<Candidate> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, random);
        return shuffled.subList(0, budget);
    }

    // follow_status = 'accepted' produces a FOLLOW notification to the followee; 'pending' produces
    // FOLLOW_REQUEST instead. Neither can target the follower themself: follows.follower_id can
    // never equal follows.following_id (no self-follow in the generated graph).
    private List<Candidate> fetchFollowCandidates() {
        List<Candidate> candidates = new ArrayList<>();
        jdbc.query(
                "SELECT follower_id, following_id, status, created_at FROM follows",
                rs -> {
                    UUID followerId = (UUID) rs.getObject("follower_id");
                    UUID followingId = (UUID) rs.getObject("following_id");
                    String status = rs.getString("status");
                    Instant createdAt = rs.getTimestamp("created_at").toInstant();
                    String type = "accepted".equals(status) ? "follow" : "follow_request";
                    candidates.add(
                            new Candidate(
                                    followingId,
                                    followerId,
                                    type,
                                    FOLLOW_ENTITY_TYPE,
                                    followerId,
                                    null,
                                    createdAt));
                });
        return candidates;
    }

    // A root comment (parent_id IS NULL) notifies the post author; a reply notifies the parent
    // comment's author. Either is skipped when the actor is the same account as the recipient,
    // matching "a user must not receive a notification for their own actions".
    private List<Candidate> fetchCommentCandidates() {
        List<Candidate> candidates = new ArrayList<>();
        jdbc.query(
                "SELECT c.id AS comment_id, c.user_id AS author_id, c.post_id AS post_id,"
                        + " c.created_at AS created_at, p.user_id AS post_author_id,"
                        + " parent.user_id AS parent_author_id"
                        + " FROM comments c"
                        + " JOIN posts p ON p.id = c.post_id"
                        + " LEFT JOIN comments parent ON parent.id = c.parent_id"
                        + " WHERE c.deleted_at IS NULL",
                rs -> {
                    UUID commentId = (UUID) rs.getObject("comment_id");
                    UUID authorId = (UUID) rs.getObject("author_id");
                    UUID postId = (UUID) rs.getObject("post_id");
                    Instant createdAt = rs.getTimestamp("created_at").toInstant();
                    UUID parentAuthorId = (UUID) rs.getObject("parent_author_id");
                    UUID postAuthorId = (UUID) rs.getObject("post_author_id");

                    UUID recipientId = parentAuthorId != null ? parentAuthorId : postAuthorId;
                    if (recipientId == null || recipientId.equals(authorId)) {
                        return;
                    }
                    String type = parentAuthorId != null ? "reply_comment" : "comment_post";
                    candidates.add(
                            new Candidate(
                                    recipientId,
                                    authorId,
                                    type,
                                    COMMENT_ENTITY_TYPE,
                                    commentId,
                                    postId,
                                    createdAt));
                });
        return candidates;
    }

    // StorySeedWriter never records a story owner's own view of their story, so every row here
    // already satisfies "no notification for one's own action" by construction.
    private List<Candidate> fetchStoryViewCandidates() {
        List<Candidate> candidates = new ArrayList<>();
        jdbc.query(
                "SELECT sv.story_id AS story_id, sv.viewer_id AS viewer_id,"
                        + " sv.viewed_at AS viewed_at, s.user_id AS owner_id"
                        + " FROM story_views sv JOIN stories s ON s.id = sv.story_id",
                rs -> {
                    UUID storyId = (UUID) rs.getObject("story_id");
                    UUID viewerId = (UUID) rs.getObject("viewer_id");
                    UUID ownerId = (UUID) rs.getObject("owner_id");
                    Instant viewedAt = rs.getTimestamp("viewed_at").toInstant();
                    candidates.add(
                            new Candidate(
                                    ownerId,
                                    viewerId,
                                    "story_view",
                                    STORY_ENTITY_TYPE,
                                    storyId,
                                    null,
                                    viewedAt));
                });
        return candidates;
    }

    // A warning notification always carries a null actor_id (the platform, not a person, per
    // notification/DATA_RULES.md), is never suppressed by a user setting or a block, and is kept
    // unsampled below since warnings are rare (~40) and the narratively important type this task
    // was specifically asked to cross-reference.
    private List<Candidate> fetchWarningCandidates() {
        List<Candidate> candidates = new ArrayList<>();
        jdbc.query(
                "SELECT user_id, created_at FROM user_warnings",
                rs -> {
                    UUID userId = (UUID) rs.getObject("user_id");
                    Instant createdAt = rs.getTimestamp("created_at").toInstant();
                    candidates.add(
                            new Candidate(userId, null, "warning", null, null, null, createdAt));
                });
        return candidates;
    }

    // Excludes a self-like the same way fetchCommentCandidates excludes a self-comment-notify -
    // EngagementSeedWriter's own generation may already rule this out, but the guard costs nothing
    // and keeps this reader correct even if that changes.
    private List<Candidate> fetchLikePostCandidates() {
        List<Candidate> candidates = new ArrayList<>();
        jdbc.query(
                "SELECT pl.post_id AS post_id, pl.user_id AS liker_id, pl.created_at AS"
                        + " created_at, p.user_id AS author_id FROM post_likes pl JOIN posts p ON"
                        + " p.id = pl.post_id WHERE pl.user_id <> p.user_id",
                rs -> {
                    UUID postId = (UUID) rs.getObject("post_id");
                    UUID likerId = (UUID) rs.getObject("liker_id");
                    UUID authorId = (UUID) rs.getObject("author_id");
                    Instant createdAt = rs.getTimestamp("created_at").toInstant();
                    candidates.add(
                            new Candidate(
                                    authorId,
                                    likerId,
                                    "like_post",
                                    POST_ENTITY_TYPE,
                                    postId,
                                    null,
                                    createdAt));
                });
        return candidates;
    }

    private List<Candidate> fetchLikeCommentCandidates() {
        List<Candidate> candidates = new ArrayList<>();
        jdbc.query(
                "SELECT cl.comment_id AS comment_id, cl.user_id AS liker_id, cl.created_at AS"
                        + " created_at, c.user_id AS author_id, c.post_id AS post_id FROM"
                        + " comment_likes cl JOIN comments c ON c.id = cl.comment_id WHERE"
                        + " cl.user_id <> c.user_id",
                rs -> {
                    UUID commentId = (UUID) rs.getObject("comment_id");
                    UUID likerId = (UUID) rs.getObject("liker_id");
                    UUID authorId = (UUID) rs.getObject("author_id");
                    UUID postId = (UUID) rs.getObject("post_id");
                    Instant createdAt = rs.getTimestamp("created_at").toInstant();
                    candidates.add(
                            new Candidate(
                                    authorId,
                                    likerId,
                                    "like_comment",
                                    COMMENT_ENTITY_TYPE,
                                    commentId,
                                    postId,
                                    createdAt));
                });
        return candidates;
    }

    // The recipient is every other participant in the message's conversation - every seeded
    // conversation is strictly 2-participant (MessageSeedWriter's own invariant), so this is
    // always exactly one row per visible message. Hidden messages (either tombstone) are excluded:
    // a notification pointing at a message the read path would withhold has nothing to show.
    private List<Candidate> fetchMessageCandidates() {
        List<Candidate> candidates = new ArrayList<>();
        jdbc.query(
                "SELECT m.id AS message_id, m.sender_id AS sender_id, m.created_at AS created_at,"
                        + " cp.user_id AS recipient_id FROM messages m JOIN"
                        + " conversation_participants cp ON cp.conversation_id ="
                        + " m.conversation_id WHERE m.sender_id IS NOT NULL AND cp.user_id <>"
                        + " m.sender_id AND m.is_deleted = false AND m.admin_removed_at IS NULL",
                rs -> {
                    UUID messageId = (UUID) rs.getObject("message_id");
                    UUID senderId = (UUID) rs.getObject("sender_id");
                    UUID recipientId = (UUID) rs.getObject("recipient_id");
                    Instant createdAt = rs.getTimestamp("created_at").toInstant();
                    candidates.add(
                            new Candidate(
                                    recipientId,
                                    senderId,
                                    "message",
                                    MESSAGE_ENTITY_TYPE,
                                    messageId,
                                    null,
                                    createdAt));
                });
        return candidates;
    }

    // No seed content encodes a literal @mention, so this pairs a small fixed-size random sample
    // of published posts with a random different user as the "mentioned" recipient. The actor is
    // the post's own author, matching CommentNotificationConsumer's precedent that a mention's
    // actor is whoever wrote the mentioning content, not the mentioned user.
    private List<Candidate> fetchMentionPostCandidates(Random random) {
        List<Object[]> posts = new ArrayList<>();
        jdbc.query(
                "SELECT id, user_id, created_at FROM posts WHERE status = 'published'",
                rs -> {
                    posts.add(
                            new Object[] {
                                rs.getObject("id"),
                                rs.getObject("user_id"),
                                rs.getTimestamp("created_at").toInstant()
                            });
                });
        return synthesizeMentions(posts, "mention_post", POST_ENTITY_TYPE, false, random);
    }

    private List<Candidate> fetchMentionCommentCandidates(Random random) {
        List<Object[]> comments = new ArrayList<>();
        jdbc.query(
                "SELECT id, user_id, post_id, created_at FROM comments WHERE deleted_at IS NULL"
                        + " AND moderation_status = 'approved'",
                rs -> {
                    comments.add(
                            new Object[] {
                                rs.getObject("id"),
                                rs.getObject("user_id"),
                                rs.getObject("post_id"),
                                rs.getTimestamp("created_at").toInstant()
                            });
                });
        return synthesizeMentions(comments, "mention_comment", COMMENT_ENTITY_TYPE, true, random);
    }

    // Shared draw for both mention flavors: rows are {id, authorId, createdAt} for posts and
    // {id, authorId, postId, createdAt} for comments - hasPostId picks which shape to read.
    private List<Candidate> synthesizeMentions(
            List<Object[]> sources,
            String type,
            String entityType,
            boolean hasPostId,
            Random random) {
        List<Candidate> candidates = new ArrayList<>();
        if (sources.isEmpty()) {
            return candidates;
        }
        List<UUID> userIds = fetchAllUserIds();
        if (userIds.size() < 2) {
            return candidates;
        }
        for (int i = 0; i < MENTION_SAMPLE_SIZE; i++) {
            Object[] source = sources.get(random.nextInt(sources.size()));
            UUID entityId = (UUID) source[0];
            UUID authorId = (UUID) source[1];
            UUID postId = hasPostId ? (UUID) source[2] : null;
            Instant createdAt = (Instant) source[hasPostId ? 3 : 2];
            UUID mentionedUserId = randomOtherUser(userIds, authorId, random);
            if (mentionedUserId == null) {
                continue;
            }
            candidates.add(
                    new Candidate(
                            mentionedUserId,
                            authorId,
                            type,
                            entityType,
                            entityId,
                            postId,
                            createdAt));
        }
        return candidates;
    }

    private List<UUID> fetchAllUserIds() {
        List<UUID> ids = new ArrayList<>();
        jdbc.query(
                "SELECT id FROM users",
                rs -> {
                    ids.add((UUID) rs.getObject("id"));
                });
        return ids;
    }

    private UUID randomOtherUser(List<UUID> userIds, UUID excluded, Random random) {
        for (int attempt = 0; attempt < 10; attempt++) {
            UUID candidate = userIds.get(random.nextInt(userIds.size()));
            if (!candidate.equals(excluded)) {
                return candidate;
            }
        }
        return null;
    }

    private void bindRow(PreparedStatement ps, Object[] row) throws SQLException {
        ps.setObject(1, row[0]);
        ps.setObject(2, row[1]);
        if (row[2] == null) {
            ps.setNull(3, Types.OTHER);
        } else {
            ps.setObject(3, row[2]);
        }
        ps.setString(4, (String) row[3]);
        if (row[4] == null) {
            ps.setNull(5, Types.VARCHAR);
        } else {
            ps.setString(5, (String) row[4]);
        }
        if (row[5] == null) {
            ps.setNull(6, Types.OTHER);
        } else {
            ps.setObject(6, row[5]);
        }
        if (row[6] == null) {
            ps.setNull(7, Types.OTHER);
        } else {
            ps.setObject(7, row[6]);
        }
        ps.setBoolean(8, (Boolean) row[7]);
        if (row[8] == null) {
            ps.setNull(9, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            ps.setTimestamp(9, (Timestamp) row[8]);
        }
        ps.setTimestamp(10, (Timestamp) row[9]);
    }

    private record Candidate(
            UUID recipientId,
            UUID actorId,
            String type,
            String entityType,
            UUID entityId,
            UUID postId,
            Instant createdAt) {}
}
