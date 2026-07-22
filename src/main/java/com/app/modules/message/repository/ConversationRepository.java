package com.app.modules.message.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.app.modules.message.entity.Conversation;

/** Persistence access for {@link Conversation}. */
@Repository
public interface ConversationRepository
        extends JpaRepository<Conversation, UUID>, ConversationRepositoryCustom {

    /**
     * Finds the existing 1-1 conversation between the two users, regardless of either participant's
     * {@code left_at} state, so a re-initiated conversation is reused rather than duplicated.
     */
    @Query(
            value =
                    """
					SELECT c.* FROM conversations c
					WHERE c.is_group = FALSE
					AND EXISTS (SELECT 1 FROM conversation_participants p1
								WHERE p1.conversation_id = c.id AND p1.user_id = :userA)
					AND EXISTS (SELECT 1 FROM conversation_participants p2
								WHERE p2.conversation_id = c.id AND p2.user_id = :userB)
					AND (SELECT COUNT(*) FROM conversation_participants px
						WHERE px.conversation_id = c.id) = 2
					LIMIT 1
					""",
            nativeQuery = true)
    Optional<Conversation> findDirectConversationBetween(UUID userA, UUID userB);

    /**
     * First page of the caller's active conversations, newest activity first.
     *
     * <p>Conversations with no message yet ({@code last_message_at IS NULL}) sort last, per {@code
     * NULLS LAST}; continuing past them onto later pages is handled by {@link
     * #findMyConversationsBefore}.
     */
    @Query(
            value =
                    """
					SELECT c.* FROM conversations c
					JOIN conversation_participants p
						ON p.conversation_id = c.id AND p.user_id = :userId AND p.left_at IS NULL
					ORDER BY c.last_message_at DESC NULLS LAST, c.id DESC
					""",
            nativeQuery = true)
    List<Conversation> findFirstMyConversations(UUID userId, Pageable pageable);

    /**
     * Keyset continuation after the {@code (lastMessageAt, conversationId)} cursor.
     *
     * <p>{@code cursorTime} is {@code null} when the cursor itself points at a {@code
     * last_message_at IS NULL} row; continuation then stays within that null group, ordered by
     * {@code id DESC}. Otherwise every null row is included unconditionally, since {@code NULLS
     * LAST} always places the whole null group after every non-null row.
     */
    @Query(
            value =
                    """
					SELECT c.* FROM conversations c
					JOIN conversation_participants p
						ON p.conversation_id = c.id AND p.user_id = :userId AND p.left_at IS NULL
					WHERE (CAST(:cursorTime AS timestamptz) IS NOT NULL
							AND (c.last_message_at < :cursorTime
								OR (c.last_message_at = :cursorTime AND c.id < :cursorId)
								OR c.last_message_at IS NULL))
						OR (CAST(:cursorTime AS timestamptz) IS NULL
							AND c.last_message_at IS NULL AND c.id < :cursorId)
					ORDER BY c.last_message_at DESC NULLS LAST, c.id DESC
					""",
            nativeQuery = true)
    List<Conversation> findMyConversationsBefore(
            UUID userId, OffsetDateTime cursorTime, UUID cursorId, Pageable pageable);
}
