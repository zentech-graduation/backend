package com.app.modules.mail.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A body to render through the shared pipeline.
 *
 * @param body Markdown; may be empty while the author is still typing
 */
public record PreviewMailCampaignRequest(
        @Schema(description = "Markdown body", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                @Size(max = 20000)
                String body) {}
