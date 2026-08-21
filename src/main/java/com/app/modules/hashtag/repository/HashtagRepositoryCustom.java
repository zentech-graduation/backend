package com.app.modules.hashtag.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.app.modules.hashtag.entity.Hashtag;
import com.app.modules.hashtag.enums.HashtagStatus;

/**
 * Status-spanning reads over {@code hashtags} for the administrative surface.
 *
 * <p>Every method here deliberately omits the {@code status = 'active'} predicate that every public
 * hashtag read carries, because an administrator managing the registry has to see the banned and
 * deleted rows it is managing. The inversion is contained to this interface for that reason: no
 * public read path can pick up an unfiltered query by accident.
 */
public interface HashtagRepositoryCustom {

    /**
     * Lists hashtags newest first, optionally narrowed to one status.
     *
     * <p>Keyset ordering is {@code (created_at DESC, id DESC)}; pass both cursor components or
     * neither.
     *
     * @param status status to match, or null for every status
     * @param cursorCreatedAt exclusive upper bound on {@code created_at}, or null for the first
     *     page
     * @param cursorId tiebreaker paired with {@code cursorCreatedAt}
     * @param limit maximum rows to return; callers over-fetch by one to detect a further page
     * @return matching hashtags in keyset order
     */
    List<Hashtag> findAdminPage(
            HashtagStatus status, OffsetDateTime cursorCreatedAt, UUID cursorId, int limit);

    /**
     * Searches hashtags by case-insensitive substring of the name, optionally narrowed to one
     * status.
     *
     * <p>Spans every status, unlike the public search. Substring rather than trigram similarity: an
     * administrator looking for a term it is about to ban needs the match to be predictable rather
     * than ranked, and a similarity threshold can silently miss the exact term.
     *
     * @param query trimmed search text, already length-checked by the caller
     * @param status status to match, or null for every status
     * @param cursorCreatedAt exclusive upper bound on {@code created_at}, or null for the first
     *     page
     * @param cursorId tiebreaker paired with {@code cursorCreatedAt}
     * @param limit maximum rows to return; callers over-fetch by one to detect a further page
     * @return matching hashtags in keyset order
     */
    List<Hashtag> searchAdmin(
            String query,
            HashtagStatus status,
            OffsetDateTime cursorCreatedAt,
            UUID cursorId,
            int limit);
}
