package com.app.modules.notification.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.app.modules.notification.entity.enums.NotificationType;

import io.swagger.v3.oas.annotations.media.Schema;

/** Single notification item returned to the client. */
@Schema(description = "Single notification item")
public record NotificationResponse(
        @Schema(description = "Notification ID", requiredMode = REQUIRED) UUID id,
        @Schema(
                        description =
                                "ID of the user who triggered the notification; null if that user"
                                        + " was deleted")
                UUID actorId,
        @Schema(description = "Notification type", example = "follow", requiredMode = REQUIRED)
                NotificationType type,
        @Schema(
                        description =
                                "Polymorphic entity type, e.g. 'post' or 'comment'; null for"
                                        + " follow events")
                String entityType,
        @Schema(description = "Polymorphic entity ID; null for follow events") UUID entityId,
        @Schema(description = "Whether this notification has been read", requiredMode = REQUIRED)
                boolean isRead,
        @Schema(description = "Timestamp when the notification was read; null if unread")
                OffsetDateTime readAt,
        @Schema(
                        description = "Timestamp when the notification was created",
                        requiredMode = REQUIRED)
                OffsetDateTime createdAt) {}
