package com.app.common.seed.model;

/**
 * Parsed content of one entry in {@code moderation_cases.json}'s {@code supplementary_actions}
 * array: a standalone {@code admin_actions} row not attached to any of the six narrative cases.
 *
 * <p>Every field below is optional except {@code at} and {@code actionType} - the supplementary
 * entries use whichever subset of the union applies to their own {@code action_type} (a {@code
 * warn_user} row carries no {@code hashtagName}, a {@code create_hashtag} row carries no {@code
 * targetUser}), so every other field is nullable by construction rather than typed per variant, the
 * same choice {@link ModerationCaseSeed} makes for its own timeline entries.
 */
public record ModerationSupplementaryAction(
        String at,
        String actor,
        String actionType,
        String targetUser,
        String note,
        String reasonKey,
        String reason,
        String triggeredBy,
        Integer durationDays,
        String targetPost,
        String targetCommentRef,
        String targetStoryRef,
        String targetConversationId,
        Integer targetMessageIndex,
        String hashtagName,
        String fromRole,
        String toRole) {}
