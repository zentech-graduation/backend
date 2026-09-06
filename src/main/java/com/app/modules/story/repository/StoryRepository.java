package com.app.modules.story.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.app.modules.story.entity.Story;

/**
 * Persistence access for {@link Story}.
 *
 * <p>JPQL queries inherit the {@code deleted_at IS NULL} filter from the entity's
 * {@code @SQLRestriction}; the expiry filter is an explicit predicate because expired rows stay in
 * the table until the cleanup job removes them.
 */
@Repository
public interface StoryRepository extends JpaRepository<Story, UUID> {

    /** Returns the story only if it is neither soft-deleted nor expired. */
    @Query("SELECT s FROM Story s WHERE s.id = :id AND s.expiresAt > :now")
    Optional<Story> findActiveById(UUID id, OffsetDateTime now);

    /** Active stories of one user in playback order (oldest first). */
    @Query(
            "SELECT s FROM Story s WHERE s.userId = :userId AND s.expiresAt > :now"
                    + " ORDER BY s.createdAt ASC")
    List<Story> findActiveByUser(UUID userId, OffsetDateTime now);

    /** Active stories of the given authors, grouped per author in playback order. */
    @Query(
            "SELECT s FROM Story s WHERE s.userId IN :authorIds AND s.expiresAt > :now"
                    + " ORDER BY s.userId, s.createdAt ASC")
    List<Story> findActiveByAuthors(List<UUID> authorIds, OffsetDateTime now);

    /**
     * Re-reads the trigger-maintained view counter, bypassing the possibly stale first-level cached
     * entity.
     */
    @Query("SELECT s.viewCount FROM Story s WHERE s.id = :storyId")
    int findViewCount(UUID storyId);

    /**
     * Re-reads the trigger-maintained like counter, bypassing the possibly stale first-level cached
     * entity.
     */
    @Query("SELECT s.likeCount FROM Story s WHERE s.id = :storyId")
    int findLikeCount(UUID storyId);

    /**
     * Hard-deletes rows that are BOTH soft-deleted AND expired (DATA_RULES §3B); expired-but-live
     * rows are never cleanup targets.
     *
     * <p>Native because it must see soft-deleted rows past the {@code @SQLRestriction} filter.
     * {@code story_views} rows follow via {@code ON DELETE CASCADE}.
     */
    @Modifying
    @Query(
            value = "DELETE FROM stories WHERE deleted_at IS NOT NULL AND expires_at < NOW()",
            nativeQuery = true)
    int purgeSoftDeletedExpired();

    /**
     * Reads the owner of a story whatever its soft-delete state, for the moderation path.
     *
     * <p>Native because a moderator acts on a story that is already removed as readily as on a live
     * one, and the entity's {@code @SQLRestriction} would hide the removed case.
     *
     * @param storyId story identifier
     * @return the author's id, or empty when no row holds that id
     */
    @Query(value = "SELECT s.user_id FROM stories s WHERE s.id = :storyId", nativeQuery = true)
    Optional<UUID> findOwnerIdIncludingDeleted(UUID storyId);

    /**
     * Reports whether a story is currently soft-deleted, spanning the {@code @SQLRestriction}.
     *
     * @param storyId story identifier
     * @return true when the row carries a {@code deleted_at}, or empty when no row holds that id
     */
    @Query(
            value = "SELECT s.deleted_at IS NOT NULL FROM stories s WHERE s.id = :storyId",
            nativeQuery = true)
    Optional<Boolean> isDeletedIncludingDeleted(UUID storyId);

    /**
     * Sets or clears a story's soft-delete marker on behalf of the moderation path.
     *
     * <p>Native for the same reason as the two reads above. Expiry is deliberately untouched: a
     * restore returns the row to the live state it held and lets {@code expires_at} continue to
     * decide visibility, so a story that expired while removed does not come back into any feed.
     *
     * @param storyId story identifier
     * @param deletedAt soft-delete timestamp, or null when restoring
     * @return number of updated stories
     */
    @Modifying
    @Query(
            value = "UPDATE stories SET deleted_at = :deletedAt WHERE id = :storyId",
            nativeQuery = true)
    int applyAdminModeration(UUID storyId, OffsetDateTime deletedAt);
}
