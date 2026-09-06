package com.app.modules.comment.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Comment module settings bound from the {@code app.comment} namespace.
 *
 * <p>{@code slowModeSeconds} of 0 disables slow mode; {@code blockedWords} empty disables the
 * blocked-word moderation rule.
 */
@ConfigurationProperties(prefix = "app.comment")
public record CommentProperties(
        int slowModeSeconds, int idempotencyTtlHours, List<String> blockedWords) {

    public CommentProperties {
        idempotencyTtlHours = idempotencyTtlHours <= 0 ? 24 : idempotencyTtlHours;
        blockedWords = blockedWords != null ? blockedWords : List.of();
    }
}
