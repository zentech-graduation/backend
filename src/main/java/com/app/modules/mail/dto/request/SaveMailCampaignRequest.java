package com.app.modules.mail.dto.request;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A campaign draft.
 *
 * @param templateKey which sample this started from, for reporting only; null when written from
 *     scratch
 * @param subject the mail subject
 * @param body Markdown, rendered server-side by the one shared pipeline
 * @param recipientUserIds the hand-picked recipients, at most ten
 * @param scheduledAt when to send, or null to leave unscheduled
 */
public record SaveMailCampaignRequest(
        @Schema(description = "Template this campaign started from", nullable = true)
                @Size(max = 100)
                String templateKey,
        @Schema(description = "Mail subject", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(max = 200)
                String subject,
        @Schema(description = "Markdown body", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(max = 20000)
                String body,
        @Schema(
                        description = "Recipients, at most ten",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotEmpty
                @Size(max = 10)
                List<UUID> recipientUserIds,
        @Schema(description = "When to send", nullable = true) OffsetDateTime scheduledAt) {}
