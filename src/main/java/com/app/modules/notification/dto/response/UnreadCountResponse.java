package com.app.modules.notification.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/** Unread notification count for the authenticated user. */
@Schema(description = "Unread notification count")
public record UnreadCountResponse(
        @Schema(
                        description = "Number of unread notifications",
                        example = "5",
                        requiredMode = REQUIRED)
                long unreadCount) {}
