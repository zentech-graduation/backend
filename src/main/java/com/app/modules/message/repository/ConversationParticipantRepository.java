package com.app.modules.message.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.app.modules.message.entity.ConversationParticipant;
import com.app.modules.message.entity.ConversationParticipantId;

/** Persistence access for {@link ConversationParticipant}. */
@Repository
public interface ConversationParticipantRepository
        extends JpaRepository<ConversationParticipant, ConversationParticipantId> {

    /** The participant gate: true only for a user actively participating (not left). */
    boolean existsByIdConversationIdAndIdUserIdAndLeftAtIsNull(UUID conversationId, UUID userId);

    Optional<ConversationParticipant> findByIdConversationIdAndIdUserId(
            UUID conversationId, UUID userId);

    /** All members (active and former) of one conversation, for detail/participant-list views. */
    List<ConversationParticipant> findByIdConversationIdOrderByJoinedAtAsc(UUID conversationId);

    /**
     * Active members across many conversations in one query, for batched conversation-list
     * hydration (avoids one query per conversation).
     */
    List<ConversationParticipant> findByIdConversationIdInAndLeftAtIsNull(
            Collection<UUID> conversationIds);

    int countByIdConversationIdAndLeftAtIsNull(UUID conversationId);
}
