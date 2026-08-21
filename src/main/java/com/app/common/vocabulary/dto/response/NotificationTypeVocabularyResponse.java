package com.app.common.vocabulary.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One row of {@code notification_type_configs}.
 *
 * @param key the {@code notification_type} value this row describes
 * @param displayName the label to render
 * @param templateKey the rendering template this type uses, or null when it has none
 * @param isUserToggleable whether an account may switch this type off in its settings
 * @param isEnabled whether the type is currently produced at all
 */
@Schema(description = "One notification type with its display metadata")
public record NotificationTypeVocabularyResponse(
        @Schema(description = "Notification type value", example = "like_post") String key,
        @Schema(description = "Label to render", example = "Like on Post") String displayName,
        @Schema(description = "Rendering template key", nullable = true) String templateKey,
        @Schema(
                        description =
                                "Whether an account may switch this type off. A moderation warning"
                                        + " is not toggleable, because an account that could"
                                        + " silence it would be disciplined without being told.")
                boolean isUserToggleable,
        @Schema(description = "Whether the type is currently produced") boolean isEnabled) {}
