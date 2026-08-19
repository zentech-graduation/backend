package com.app.modules.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Payload for handing a report up to an administrator.
 *
 * <p>Carries no report identifier of its own, unlike {@link AdminActionRequest}: the report being
 * escalated is the one in the path, and a second identifier in the body could only disagree with
 * it.
 */
@Schema(description = "Payload for escalating a report to an administrator")
public record AdminEscalateReportRequest(
        @Schema(
                        description = "Why the decision is being handed up",
                        example = "Reported account is a moderator; outside my remit",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(max = 2000)
                String reason) {}
