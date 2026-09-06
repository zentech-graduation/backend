package com.app.modules.message.repository;

import java.util.UUID;

/** Projection for a batched per-conversation unread-message count. */
public interface ConversationUnreadCount {

    UUID getConversationId();

    long getUnreadCount();
}
