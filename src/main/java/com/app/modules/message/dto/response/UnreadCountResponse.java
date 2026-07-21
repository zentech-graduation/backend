package com.app.modules.message.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** API response for the caller's total unread message count across all conversations. */
@Schema(description = "Total unread message count")
public record UnreadCountResponse(
        @Schema(description = "Total unread messages across all active conversations.")
                long unreadCount) {}
