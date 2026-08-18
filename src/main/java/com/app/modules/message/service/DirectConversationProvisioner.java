package com.app.modules.message.service;

import java.util.UUID;

/**
 * Creates and discards the 1-1 conversation implied by a mutual follow.
 *
 * <p>This port exists to keep the bean graph acyclic. The message module already depends on the
 * social module, so the social module cannot depend on {@code ConversationService} in return.
 * Implementations must therefore reach the database directly and must never call back into the
 * social module.
 */
public interface DirectConversationProvisioner {

    /**
     * Ensures a conversation exists for the pair, doing nothing when one already does.
     *
     * <p>Safe to call repeatedly and in either argument order.
     *
     * @param userIdA one participant
     * @param userIdB the other participant
     */
    void ensureDirectConversation(UUID userIdA, UUID userIdB);

    /**
     * Removes the pair's conversation only when no message was ever sent in it.
     *
     * <p>A conversation carrying history is left untouched: ending a follow must not destroy the
     * record of what was said.
     *
     * @param userIdA one participant
     * @param userIdB the other participant
     */
    void discardEmptyDirectConversation(UUID userIdA, UUID userIdB);
}
