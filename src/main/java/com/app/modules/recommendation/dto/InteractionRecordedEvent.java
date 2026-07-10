package com.app.modules.recommendation.dto;

import java.util.UUID;

/**
 * Payload of {@code rec.interaction.recorded.v1}, describing one server-side engagement.
 *
 * @param eventType one of the {@code event_type} enum values (e.g. {@code post_like})
 * @param entityType entity class ({@code post}, {@code hashtag}, {@code story}); matches {@code
 *     user_events.entity_type}
 * @param entityId target entity id; inserted into {@code user_events.entity_id} (no FK, so history
 *     survives post hard-delete)
 * @param targetUserId author of the target entity when known; used by the trending consumer for
 *     self-content exclusion, null when unknown
 * @param sessionId client session id when available, null otherwise
 * @param platform {@code ios}, {@code android}, or {@code web}; null allowed
 */
public record InteractionRecordedEvent(
        String eventType,
        String entityType,
        UUID entityId,
        UUID targetUserId,
        UUID sessionId,
        String platform) {}
