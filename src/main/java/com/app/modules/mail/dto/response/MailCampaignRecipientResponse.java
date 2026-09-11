package com.app.modules.mail.dto.response;

import java.util.UUID;

import com.app.modules.mail.enums.MailCampaignRecipientStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One recipient and what happened to them.
 *
 * @param userId the account
 * @param username the account's handle at read time
 * @param resolvedEmail the address used at send time, null before the send
 * @param status the outcome, including a deliberate skip for opting out
 */
public record MailCampaignRecipientResponse(
        @Schema(description = "The account") UUID userId,
        @Schema(description = "Handle at read time", nullable = true) String username,
        @Schema(description = "Address used at send time", nullable = true) String resolvedEmail,
        @Schema(description = "Outcome for this recipient") MailCampaignRecipientStatus status) {}
