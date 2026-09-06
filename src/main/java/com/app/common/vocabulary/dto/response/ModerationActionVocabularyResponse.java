package com.app.common.vocabulary.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One row of {@code moderation_action_configs}.
 *
 * @param key the {@code admin_action_type} value this row describes
 * @param displayName the label to render in an audit listing
 * @param requiresReason whether the action refuses to proceed without a written reason
 * @param isReversible whether an inverse action exists
 * @param isEnabled whether the action is currently offered
 */
@Schema(description = "One moderation action type with its display metadata")
public record ModerationActionVocabularyResponse(
        @Schema(description = "Moderation action type value", example = "ban_user") String key,
        @Schema(description = "Label to render", example = "Ban User") String displayName,
        @Schema(description = "Whether the action requires a written reason")
                boolean requiresReason,
        @Schema(description = "Whether an inverse action exists") boolean isReversible,
        @Schema(description = "Whether the action is currently offered") boolean isEnabled) {}
