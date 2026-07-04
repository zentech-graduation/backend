package com.app.modules.comment.service;

/** Synchronous, rule-based moderation applied to normalized comment content before persistence. */
public interface CommentModerationService {

    /** Outcome of a moderation check; {@code reason} is null when not rejected. */
    record ModerationResult(boolean rejected, String reason) {

        public static ModerationResult approved() {
            return new ModerationResult(false, null);
        }

        public static ModerationResult rejected(String reason) {
            return new ModerationResult(true, reason);
        }
    }

    /**
     * Evaluates already-normalized content against the moderation rules.
     *
     * @param normalizedContent content already passed through {@code CommentContentNormalizer}
     * @return the moderation result; rejected results carry a machine-readable reason
     */
    ModerationResult check(String normalizedContent);
}
