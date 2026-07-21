package com.app.modules.message.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Message module settings bound from the {@code app.message} namespace.
 *
 * <p>{@code groupChatEnabled} gates all group-conversation operations; there is no runtime {@code
 * feature_flags} reader in this codebase, so this config toggle - not the decorative {@code
 * group_chat} row in that table - is the actual switch. {@code maxGroupParticipants} bounds group
 * size excluding the creator.
 */
@ConfigurationProperties(prefix = "app.message")
public record MessageProperties(
        int idempotencyTtlHours, boolean groupChatEnabled, int maxGroupParticipants) {

    public MessageProperties {
        idempotencyTtlHours = idempotencyTtlHours <= 0 ? 24 : idempotencyTtlHours;
        maxGroupParticipants = maxGroupParticipants <= 0 ? 256 : maxGroupParticipants;
    }
}
