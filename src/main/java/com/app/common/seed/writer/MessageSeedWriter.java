package com.app.common.seed.writer;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.app.common.seed.loader.SeedContent;
import com.app.common.seed.model.ConversationSeed;
import com.app.common.seed.model.MessageSeed;
import com.app.common.seed.time.SeedTimeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seeds {@code conversations} (60, strictly 2-participant), {@code conversation_participants} and
 * {@code messages} (~2,200) from {@code conversations.json}.
 *
 * <p><b>Column set</b>: the running schema (V50) removed {@code is_group}/{@code group_name}/{@code
 * group_avatar_url} from {@code conversations} - group conversations were a product decision that
 * was reversed, and every group-shaped row now lives in {@code archived_group_conversations}
 * instead. This writer never references those three columns; every conversation it inserts is the
 * live 2-participant shape, matching {@code direct_pair_key}'s uniqueness constraint.
 *
 * <p><b>{@code last_message_at}</b>: computed only after every one of a conversation's messages has
 * been inserted (the newest {@code created_at} among its non-deleted messages, {@code null} if
 * every message in the conversation is sender-deleted or the conversation has none), never guessed
 * up front, matching the invariant this table's derived-data contract states.
 *
 * <p><b>Two independent tombstones</b> (see {@code message/DATA_RULES.md} Section 1 and the
 * Domain-Specific Invariants in {@code STRUCT.md}): a sender's own deletion sets {@code is_deleted
 * = TRUE} and {@code deleted_at}, and clears {@code content}, which is irreversible by design. An
 * administrative removal sets {@code admin_removed_at} alone and preserves {@code content} so a
 * restore can return the message. The two are independent - this writer applies whichever one (or
 * neither, or in principle both) {@code conversations.json}'s per-message {@code is_deleted} /
 * {@code admin_removed_at_offset_minutes} fields encode.
 */
@Slf4j
@Service
@Profile("dev")
@RequiredArgsConstructor
public class MessageSeedWriter {

    private static final String INSERT_CONVERSATION_SQL =
            "INSERT INTO conversations (id, direct_pair_key, created_by, created_at, updated_at)"
                    + " VALUES (?, ?, ?, ?, ?)";
    private static final String INSERT_PARTICIPANT_SQL =
            "INSERT INTO conversation_participants (conversation_id, user_id, joined_at,"
                    + " is_manually_unread) VALUES (?, ?, ?, ?)";
    private static final String INSERT_MESSAGE_SQL =
            "INSERT INTO messages (id, conversation_id, sender_id, message_type, content,"
                    + " is_deleted, deleted_at, admin_removed_at, created_at) VALUES (?, ?, ?,"
                    + " ?::message_type, ?, ?, ?, ?, ?)";
    private static final String UPDATE_LAST_MESSAGE_AT_SQL =
            "UPDATE conversations SET last_message_at = ? WHERE id = ?";
    private static final String UPDATE_LAST_READ_AT_SQL =
            "UPDATE conversation_participants SET last_read_at = ?"
                    + " WHERE conversation_id = ? AND user_id = ?";

    private final JdbcTemplate jdbc;

    /**
     * Inserts every conversation from {@code conversations.json} with its participants and
     * messages, then back-fills each conversation's {@code last_message_at} and each read
     * participant's {@code last_read_at} once every message is known.
     *
     * @param usersByUsername username-to-id map produced by {@link UserSeedWriter#write}
     */
    public void write(
            SeedContent content, Map<String, UUID> usersByUsername, SeedTimeline timeline) {
        Map<UUID, Instant> createdAtByUserId = fetchCreatedAtByUserId();

        List<Object[]> conversationRows = new ArrayList<>();
        List<Object[]> participantRows = new ArrayList<>();
        List<Object[]> messageRows = new ArrayList<>();
        List<PendingConversation> pending = new ArrayList<>();

        for (ConversationSeed conversation : content.conversations()) {
            List<String> participants = conversation.participants();
            if (participants.size() != 2) {
                throw new IllegalStateException(
                        "MessageSeedWriter: conversation '"
                                + conversation.id()
                                + "' has "
                                + participants.size()
                                + " participants - only strictly 2-participant conversations are"
                                + " supported");
            }
            String usernameA = participants.get(0);
            String usernameB = participants.get(1);
            UUID userIdA = requireUser(usersByUsername, usernameA, conversation.id());
            UUID userIdB = requireUser(usersByUsername, usernameB, conversation.id());

            Instant conversationCreatedAt =
                    timeline.conversationCreatedAt(
                            createdAtByUserId.get(userIdA), createdAtByUserId.get(userIdB));
            UUID conversationId = UUID.randomUUID();
            String directPairKey = directPairKey(userIdA, userIdB);

            conversationRows.add(
                    new Object[] {
                        conversationId,
                        directPairKey,
                        userIdA,
                        Timestamp.from(conversationCreatedAt),
                        Timestamp.from(conversationCreatedAt)
                    });

            boolean unreadA = conversation.unreadFor().contains(usernameA);
            boolean unreadB = conversation.unreadFor().contains(usernameB);
            participantRows.add(
                    new Object[] {
                        conversationId, userIdA, Timestamp.from(conversationCreatedAt), unreadA
                    });
            participantRows.add(
                    new Object[] {
                        conversationId, userIdB, Timestamp.from(conversationCreatedAt), unreadB
                    });

            Instant latestNonDeleted = null;
            for (MessageSeed message : conversation.messages()) {
                UUID senderId = requireUser(usersByUsername, message.sender(), conversation.id());
                Instant messageCreatedAt =
                        timeline.messageCreatedAt(message, conversationCreatedAt);

                boolean isDeleted = message.isDeleted();
                // The sender tombstone (is_deleted + deleted_at) is set together and clears
                // content;
                // a fixed 1-second offset is enough realism for "deleted shortly after sending" and
                // needs no dedicated randomness generator since no invariant in this task depends
                // on
                // its exact spacing, only on it never preceding the message's own created_at.
                Instant deletedAt = isDeleted ? messageCreatedAt.plusSeconds(1) : null;
                Instant adminRemovedAt =
                        message.adminRemovedAtOffsetMinutes() == null
                                ? null
                                : messageCreatedAt.plusSeconds(
                                        message.adminRemovedAtOffsetMinutes() * 60L);

                messageRows.add(
                        new Object[] {
                            UUID.randomUUID(),
                            conversationId,
                            senderId,
                            message.messageType(),
                            isDeleted ? null : message.text(),
                            isDeleted,
                            deletedAt == null ? null : Timestamp.from(deletedAt),
                            adminRemovedAt == null ? null : Timestamp.from(adminRemovedAt),
                            Timestamp.from(messageCreatedAt)
                        });

                if (!isDeleted
                        && (latestNonDeleted == null
                                || messageCreatedAt.isAfter(latestNonDeleted))) {
                    latestNonDeleted = messageCreatedAt;
                }
            }

            pending.add(
                    new PendingConversation(
                            conversationId, userIdA, unreadA, userIdB, unreadB, latestNonDeleted));
        }

        jdbc.batchUpdate(
                INSERT_CONVERSATION_SQL,
                conversationRows,
                conversationRows.size(),
                this::bindConversationRow);
        jdbc.batchUpdate(
                INSERT_PARTICIPANT_SQL,
                participantRows,
                participantRows.size(),
                this::bindParticipantRow);
        jdbc.batchUpdate(INSERT_MESSAGE_SQL, messageRows, messageRows.size(), this::bindMessageRow);
        log.info(
                "[seed] conversations: {} rows written, messages: {} rows written",
                conversationRows.size(),
                messageRows.size());

        backfillLastMessageAtAndReadState(pending);
    }

    // Runs only after every message row above is committed to the batch, so "the newest created_at
    // among a conversation's non-deleted messages" reflects every message that conversation will
    // ever have in this seed run, never a partial view computed before the last one was known.
    private void backfillLastMessageAtAndReadState(List<PendingConversation> pending) {
        List<Object[]> lastMessageAtRows = new ArrayList<>();
        List<Object[]> lastReadAtRows = new ArrayList<>();
        for (PendingConversation conversation : pending) {
            if (conversation.latestNonDeletedMessageAt() != null) {
                lastMessageAtRows.add(
                        new Object[] {
                            Timestamp.from(conversation.latestNonDeletedMessageAt()),
                            conversation.conversationId()
                        });
                // A participant not named in unread_for has read up to the newest message; one
                // named in unread_for keeps last_read_at unset (never read) per the JSON's own
                // is_manually_unread flag set at insert time.
                if (!conversation.unreadA()) {
                    lastReadAtRows.add(
                            new Object[] {
                                Timestamp.from(conversation.latestNonDeletedMessageAt()),
                                conversation.conversationId(),
                                conversation.userIdA()
                            });
                }
                if (!conversation.unreadB()) {
                    lastReadAtRows.add(
                            new Object[] {
                                Timestamp.from(conversation.latestNonDeletedMessageAt()),
                                conversation.conversationId(),
                                conversation.userIdB()
                            });
                }
            }
        }
        jdbc.batchUpdate(
                UPDATE_LAST_MESSAGE_AT_SQL,
                lastMessageAtRows,
                lastMessageAtRows.size(),
                this::bindLastMessageAtRow);
        jdbc.batchUpdate(
                UPDATE_LAST_READ_AT_SQL,
                lastReadAtRows,
                lastReadAtRows.size(),
                this::bindLastReadAtRow);
    }

    private UUID requireUser(
            Map<String, UUID> usersByUsername, String username, String conversationId) {
        UUID userId = usersByUsername.get(username);
        if (userId == null) {
            throw new IllegalStateException(
                    "MessageSeedWriter: conversation '"
                            + conversationId
                            + "' has participant '"
                            + username
                            + "' which does not exist in users.json");
        }
        return userId;
    }

    private Map<UUID, Instant> fetchCreatedAtByUserId() {
        Map<UUID, Instant> createdAtByUserId = new HashMap<>();
        jdbc.query(
                "SELECT id, created_at FROM users",
                rs -> {
                    UUID id = (UUID) rs.getObject("id");
                    Instant createdAt = rs.getTimestamp("created_at").toInstant();
                    createdAtByUserId.put(id, createdAt);
                });
        return createdAtByUserId;
    }

    /**
     * Builds the two-participant pair key {@code direct_pair_key} is stored and looked up by: the
     * smaller UUID (by {@link UUID#compareTo}) then the larger, joined with {@code "_"}, so the key
     * is identical regardless of which participant is passed first.
     *
     * <p>Package-private so {@link ModerationSeedWriter} can rebuild the same key from a {@code
     * conversations.json} entry's participant usernames to resolve a moderation timeline's {@code
     * target_conversation_id} back to the real generated conversation.
     */
    static String directPairKey(UUID userIdA, UUID userIdB) {
        return userIdA.compareTo(userIdB) <= 0 ? userIdA + "_" + userIdB : userIdB + "_" + userIdA;
    }

    private void bindConversationRow(PreparedStatement ps, Object[] row) throws SQLException {
        ps.setObject(1, row[0]);
        ps.setString(2, (String) row[1]);
        ps.setObject(3, row[2]);
        ps.setTimestamp(4, (Timestamp) row[3]);
        ps.setTimestamp(5, (Timestamp) row[4]);
    }

    private void bindParticipantRow(PreparedStatement ps, Object[] row) throws SQLException {
        ps.setObject(1, row[0]);
        ps.setObject(2, row[1]);
        ps.setTimestamp(3, (Timestamp) row[2]);
        ps.setBoolean(4, (Boolean) row[3]);
    }

    private void bindMessageRow(PreparedStatement ps, Object[] row) throws SQLException {
        ps.setObject(1, row[0]);
        ps.setObject(2, row[1]);
        ps.setObject(3, row[2]);
        ps.setString(4, (String) row[3]);
        if (row[4] == null) {
            ps.setNull(5, Types.VARCHAR);
        } else {
            ps.setString(5, (String) row[4]);
        }
        ps.setBoolean(6, (Boolean) row[5]);
        if (row[6] == null) {
            ps.setNull(7, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            ps.setTimestamp(7, (Timestamp) row[6]);
        }
        if (row[7] == null) {
            ps.setNull(8, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            ps.setTimestamp(8, (Timestamp) row[7]);
        }
        ps.setTimestamp(9, (Timestamp) row[8]);
    }

    private void bindLastMessageAtRow(PreparedStatement ps, Object[] row) throws SQLException {
        ps.setTimestamp(1, (Timestamp) row[0]);
        ps.setObject(2, row[1]);
    }

    private void bindLastReadAtRow(PreparedStatement ps, Object[] row) throws SQLException {
        ps.setTimestamp(1, (Timestamp) row[0]);
        ps.setObject(2, row[1]);
        ps.setObject(3, row[2]);
    }

    // Carries just enough about each conversation to run the last_message_at / last_read_at
    // back-fill after every message row is known.
    private record PendingConversation(
            UUID conversationId,
            UUID userIdA,
            boolean unreadA,
            UUID userIdB,
            boolean unreadB,
            Instant latestNonDeletedMessageAt) {}
}
