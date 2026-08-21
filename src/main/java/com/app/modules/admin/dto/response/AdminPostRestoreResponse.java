package com.app.modules.admin.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Outcome of restoring a post that moderation had removed.
 *
 * <p>Restore is the one write path that strips a banned hashtag instead of refusing, because a
 * moderator correcting its own removal must not be blocked by an unrelated administrator decision.
 * The post therefore comes back carrying fewer tags than its caption names, and the moderator has
 * to be told: the audit row records it, but a moderator reads a response, not an audit row.
 *
 * <p>Wraps the audit event rather than adding a field to it. The audit event describes what was
 * recorded; the dropped names describe what else happened to the post, which is not the same thing
 * and does not apply to the fourteen other actions that share that shape.
 *
 * @param action the immutable audit event written for this restore
 * @param droppedHashtags normalized names the caption still carries that were not re-associated
 *     because an administrator has banned them; empty when nothing was dropped, which is the normal
 *     case and also every restore whose result is not published
 */
@Schema(description = "Outcome of restoring a post removed by moderation")
public record AdminPostRestoreResponse(
        @Schema(description = "The immutable audit event written for this restore")
                AdminActionResponse action,
        @Schema(
                        description =
                                "Normalized hashtag names the caption still carries but that were"
                                        + " not re-associated because they are banned. Empty when"
                                        + " nothing was dropped.")
                List<String> droppedHashtags) {}
