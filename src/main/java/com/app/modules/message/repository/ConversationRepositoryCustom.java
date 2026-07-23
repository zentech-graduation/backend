package com.app.modules.message.repository;

import java.util.UUID;

/**
 * Native-SQL operations for {@link com.app.modules.message.entity.Conversation} not expressible as
 * derived queries.
 */
public interface ConversationRepositoryCustom {

    /**
     * Acquires a transaction-scoped Postgres advisory lock keyed on the unordered pair {@code
     * (userA, userB)} and returns the same canonical {@code minUserId:maxUserId} key the lock was
     * derived from.
     *
     * <p>Callers must invoke this before the check-then-create sequence in {@code
     * createDirectConversation}, inside the same transaction that performs it: the lock serializes
     * concurrent calls for the same pair, and is released automatically at transaction end.
     *
     * @param userA one participant of the candidate 1-1 conversation
     * @param userB the other participant
     * @return the canonical pair key, for use as {@code Conversation.directPairKey} on insert
     */
    String lockDirectConversationPair(UUID userA, UUID userB);
}
