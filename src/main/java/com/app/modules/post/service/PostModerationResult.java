package com.app.modules.post.service;

import java.util.UUID;

import com.app.modules.post.enums.PostStatus;

/**
 * Outcome of a moderation removal or restore.
 *
 * @param ownerId author of the moderated post, for the audit row the caller writes
 * @param status status the post holds after the operation
 */
public record PostModerationResult(UUID ownerId, PostStatus status) {}
