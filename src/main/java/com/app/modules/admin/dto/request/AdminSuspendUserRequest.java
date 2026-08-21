package com.app.modules.admin.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Audit context for a suspension, with an optional fixed duration.
 *
 * <p>An absent {@code durationDays} means the suspension is indefinite and stores a null {@code
 * suspended_until}, which no expiry path ever matches. A present value stores {@code now() +
 * durationDays}, after which the first authentication attempt or the reinstatement job returns the
 * account to active.
 */
@Schema(description = "Audit context for a suspension, with an optional fixed duration")
public record AdminSuspendUserRequest(
        @Schema(
                        description = "Human-readable reason for the suspension",
                        example = "Repeated harassment violations",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(max = 2000)
                String reason,
        @Schema(description = "Optional report that prompted the suspension") UUID reportId,
        @Schema(
                        description =
                                "Days until the suspension lapses; omit for an indefinite"
                                        + " suspension",
                        example = "7")
                @Min(1)
                @Max(3650)
                Integer durationDays) {}
