package com.app.modules.mail.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A read-only campaign sample.
 *
 * @param templateKey stable identifier
 * @param displayName the name shown in the picker
 * @param description one line explaining when to use it
 * @param body the Markdown sample, copied into the editor on selection and never written back
 */
public record MailCampaignTemplateResponse(
        @Schema(description = "Stable identifier") String templateKey,
        @Schema(description = "Name shown in the picker") String displayName,
        @Schema(description = "When to use it", nullable = true) String description,
        @Schema(description = "The Markdown sample") String body) {}
