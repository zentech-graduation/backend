package com.app.modules.admin.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Audit context for one explicit moderation operation.
 *
 * <p>Carries no structured metadata field. The {@code admin_actions.metadata} column is written
 * from server-derived facts only, so a client-supplied map has nowhere to go; because request DTOs
 * reject unrecognised properties, sending one is answered with a deserialization error rather than
 * silently discarded. Dropping it quietly would leave the caller believing its data was recorded.
 */
@Schema(description = "Audit context for one explicit moderation operation")
public record AdminActionRequest(
        @Schema(
                        description = "Human-readable reason for the moderation operation",
                        example = "Repeated harassment violations",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(max = 2000)
                String reason,
        @Schema(description = "Optional report that prompted the moderation operation")
                UUID reportId) {}
