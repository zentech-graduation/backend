package com.app.modules.comment.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.modules.comment.entity.Comment;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {

    Optional<Comment> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Reads the author of a comment regardless of its soft-delete state.
     *
     * @param commentId comment identifier
     * @return author identifier when the comment exists
     */
    @Query(value = "SELECT user_id FROM comments WHERE id = :commentId", nativeQuery = true)
    Optional<UUID> findOwnerIdIncludingDeleted(@Param("commentId") UUID commentId);

    /**
     * Determines whether a comment is soft-deleted.
     *
     * @param commentId comment identifier
     * @return true when the comment has a deletion timestamp
     */
    @Query(
            value = "SELECT deleted_at IS NOT NULL FROM comments WHERE id = :commentId",
            nativeQuery = true)
    Optional<Boolean> isDeletedIncludingDeleted(@Param("commentId") UUID commentId);

    /**
     * Applies an administrator-controlled soft-delete state to one comment.
     *
     * @param commentId comment identifier
     * @param deletedAt soft-delete timestamp, or null when restoring
     * @return number of updated comments
     */
    @Modifying
    @Query(
            value = "UPDATE comments SET deleted_at = :deletedAt WHERE id = :commentId",
            nativeQuery = true)
    int applyAdminModeration(
            @Param("commentId") UUID commentId, @Param("deletedAt") OffsetDateTime deletedAt);

    /**
     * First keyset page of approved top-level comments for a post, newest first.
     *
     * <p>Paired with {@link #findTopLevelBefore}; the no-cursor variant avoids binding an untyped
     * null timestamp.
     *
     * @param postId post whose comments are listed
     * @param pageable page size carrier (page number is always 0 for keyset paging)
     * @return top-level approved comments ordered by the {@code (created_at, id)} tuple descending
     */
    @Query(
            value =
                    "SELECT * FROM comments WHERE post_id = :postId AND parent_id IS NULL "
                            + "AND moderation_status = 'approved' AND deleted_at IS NULL "
                            + "ORDER BY created_at DESC, id DESC",
            nativeQuery = true)
    List<Comment> findFirstTopLevel(@Param("postId") UUID postId, Pageable pageable);

    /**
     * Keyset page of approved top-level comments strictly after the cursor tuple, newest first.
     *
     * <p>The {@code (created_at, id)} row-value comparison seeks directly to the cursor position
     * and never drops comments sharing a boundary {@code created_at}. Served exactly by {@code
     * idx_comments_post_root_id} (V36).
     *
     * @param postId post whose comments are listed
     * @param cursorTime {@code created_at} of the cursor row; never null
     * @param cursorId id of the cursor row, breaking ties on equal {@code created_at}; never null
     * @param pageable page size carrier
     * @return top-level approved comments ordered by the {@code (created_at, id)} tuple descending
     */
    @Query(
            value =
                    "SELECT * FROM comments WHERE post_id = :postId AND parent_id IS NULL "
                            + "AND moderation_status = 'approved' AND deleted_at IS NULL "
                            + "AND (created_at, id) < (:cursorTime, :cursorId) "
                            + "ORDER BY created_at DESC, id DESC",
            nativeQuery = true)
    List<Comment> findTopLevelBefore(
            @Param("postId") UUID postId,
            @Param("cursorTime") OffsetDateTime cursorTime,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);

    /**
     * First keyset page of approved direct replies to a parent comment, newest first.
     *
     * @param parentId parent comment whose direct replies are listed
     * @param pageable page size carrier
     * @return approved direct replies ordered by the {@code (created_at, id)} tuple descending
     */
    @Query(
            value =
                    "SELECT * FROM comments WHERE parent_id = :parentId "
                            + "AND moderation_status = 'approved' AND deleted_at IS NULL "
                            + "ORDER BY created_at DESC, id DESC",
            nativeQuery = true)
    List<Comment> findFirstReplies(@Param("parentId") UUID parentId, Pageable pageable);

    /**
     * Keyset page of approved direct replies strictly after the cursor tuple, newest first.
     *
     * <p>The {@code (created_at, id)} row-value comparison seeks directly to the cursor position
     * and never drops replies sharing a boundary {@code created_at}. Served exactly by {@code
     * idx_comments_parent_id} (V36).
     *
     * @param parentId parent comment whose direct replies are listed
     * @param cursorTime {@code created_at} of the cursor row; never null
     * @param cursorId id of the cursor row, breaking ties on equal {@code created_at}; never null
     * @param pageable page size carrier
     * @return approved direct replies ordered by the {@code (created_at, id)} tuple descending
     */
    @Query(
            value =
                    "SELECT * FROM comments WHERE parent_id = :parentId "
                            + "AND moderation_status = 'approved' AND deleted_at IS NULL "
                            + "AND (created_at, id) < (:cursorTime, :cursorId) "
                            + "ORDER BY created_at DESC, id DESC",
            nativeQuery = true)
    List<Comment> findRepliesBefore(
            @Param("parentId") UUID parentId,
            @Param("cursorTime") OffsetDateTime cursorTime,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);

    /**
     * Soft-deletes a comment and every descendant in its subtree in a single statement.
     *
     * <p>The recursive CTE walks {@code parent_id} edges from the target down. FK {@code ON DELETE
     * CASCADE} only fires on a hard delete, so descendants must be soft-deleted explicitly here.
     * Each affected row's {@code AFTER UPDATE} trigger fires, so {@code posts.comment_count} and
     * the parent {@code reply_count} decrement correctly.
     *
     * @param commentId root of the subtree to soft-delete
     * @param now soft-delete timestamp applied to every affected row
     * @return number of rows soft-deleted
     */
    @Modifying
    @Query(
            value =
                    """
					WITH RECURSIVE subtree AS (
						SELECT id FROM comments WHERE id = :commentId
						UNION ALL
						SELECT c.id FROM comments c
						JOIN subtree s ON c.parent_id = s.id
					)
					UPDATE comments
					SET deleted_at = :now
					WHERE id IN (SELECT id FROM subtree) AND deleted_at IS NULL
					""",
            nativeQuery = true)
    int softDeleteSubtree(@Param("commentId") UUID commentId, @Param("now") OffsetDateTime now);
}
