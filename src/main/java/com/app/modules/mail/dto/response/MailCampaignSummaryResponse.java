package com.app.modules.mail.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.app.modules.mail.enums.MailCampaignStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One row of campaign history.
 *
 * @param id campaign identifier
 * @param subject the mail subject
 * @param status current lifecycle state
 * @param recipientCount how many recipients it carries
 * @param createdBy the administrator who wrote it
 * @param scheduledAt when it is due, or null
 * @param sentAt when it went out, or null
 * @param createdAt when it was drafted
 */
public record MailCampaignSummaryResponse(
        @Schema(description = "Campaign identifier") UUID id,
        @Schema(description = "Mail subject") String subject,
        @Schema(description = "Lifecycle state") MailCampaignStatus status,
        @Schema(description = "Recipient count") int recipientCount,
        @Schema(description = "Author", nullable = true) UUID createdBy,
        @Schema(description = "Due at", nullable = true) OffsetDateTime scheduledAt,
        @Schema(description = "Sent at", nullable = true) OffsetDateTime sentAt,
        @Schema(description = "Drafted at") OffsetDateTime createdAt) {}
