package com.app.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Outcome of issuing one warning.
 *
 * <p>{@code resultingStatus} is stated explicitly, and is not always what the strike's consequence
 * called for. A strike never weakens a penalty the account is already under, so a seven-day
 * suspension applied to an account already banned leaves it banned. Without this field the
 * moderator could not tell whether the penalty landed.
 *
 * @param warning the warning just issued
 * @param activeWarningCount warnings now counting toward the next strike; back to zero when this
 *     one produced a strike, because a strike consumes the warnings that produced it
 * @param strikeIssued whether this warning was the third and produced a strike
 * @param strike the strike produced, or null when none was
 * @param resultingStatus the account's status after the operation, as its wire value
 */
@Schema(description = "Outcome of issuing one warning")
public record AdminWarnUserResponse(
        AdminWarningResponse warning,
        long activeWarningCount,
        boolean strikeIssued,
        AdminStrikeResponse strike,
        String resultingStatus) {}
