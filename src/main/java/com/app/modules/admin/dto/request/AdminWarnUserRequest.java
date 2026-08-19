package com.app.modules.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Payload for issuing one warning against an account.
 *
 * <p>{@code reasonKey} names a {@code report_reason_configs} row. The reasons a moderator may cite
 * are the same set a reporter may cite, and a key whose row is disabled is refused.
 */
@Schema(description = "Payload for issuing one warning against an account")
public record AdminWarnUserRequest(
        @Schema(
                        description = "Key of an enabled report_reason_configs row",
                        example = "spam",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(max = 50)
                String reasonKey,
        @Schema(
                        description = "What the account did, in the moderator's own words",
                        example = "Third bulk-DM campaign this month",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(max = 2000)
                String note) {}
