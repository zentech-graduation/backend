package com.app.modules.message.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.modules.message.entity.Message;

/** Persistence access for {@link Message}. */
@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    /**
     * One row per conversation - its most recent message - for batched conversation-list preview
     * hydration. Avoids one query per conversation.
     */
    @Query(
            value =
                    """
					SELECT DISTINCT ON (m.conversation_id) m.* FROM messages m
					WHERE m.conversation_id IN (:conversationIds)
					ORDER BY m.conversation_id, m.created_at DESC
					""",
            nativeQuery = true)
    List<Message> findLastMessagePerConversation(
            @Param("conversationIds") List<UUID> conversationIds);

    /**
     * Batched unread-message count per conversation for the given user: messages not sent by the
     * user, not deleted, and newer than the user's {@code last_read_at} (or all of them, if the
     * user has never read the conversation).
     *
     * <p>Uses {@code IS DISTINCT FROM} rather than {@code <>} so a message whose sender was later
     * deleted (nullable {@code sender_id}, V32) still counts as unread instead of being silently
     * excluded by a NULL comparison.
     */
    @Query(
            value =
                    """
					SELECT m.conversation_id AS conversationId, COUNT(*) AS unreadCount
					FROM messages m
					JOIN conversation_participants p
						ON p.conversation_id = m.conversation_id
						AND p.user_id = :userId
						AND p.left_at IS NULL
					WHERE m.conversation_id IN (:conversationIds)
					AND m.is_deleted = FALSE
					AND m.sender_id IS DISTINCT FROM :userId
					AND (p.last_read_at IS NULL OR m.created_at > p.last_read_at)
					GROUP BY m.conversation_id
					""",
            nativeQuery = true)
    List<ConversationUnreadCount> countUnreadPerConversation(
            @Param("userId") UUID userId, @Param("conversationIds") List<UUID> conversationIds);
}
