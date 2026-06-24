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
     * First keyset page of approved top-level comments for a post, newest first.
     *
     * <p>Paired with {@link #findTopLevelBefore}; the no-cursor variant avoids binding an untyped
     * null timestamp.
     *
     * @param postId post whose comments are listed
     * @param pageable page size carrier (page number is always 0 for keyset paging)
     * @return top-level approved comments ordered by {@code created_at} descending
     */
    @Query(
            "SELECT c FROM Comment c WHERE c.postId = :postId "
                    + "AND c.parentId IS NULL AND c.moderationStatus = 'approved' "
                    + "ORDER BY c.createdAt DESC")
    List<Comment> findFirstTopLevel(@Param("postId") UUID postId, Pageable pageable);

    /**
     * Keyset page of approved top-level comments older than the cursor, newest first.
     *
     * @param postId post whose comments are listed
     * @param cursor exclusive upper bound on {@code created_at}; never null
     * @param pageable page size carrier
     * @return top-level approved comments ordered by {@code created_at} descending
     */
    @Query(
            "SELECT c FROM Comment c WHERE c.postId = :postId "
                    + "AND c.parentId IS NULL AND c.moderationStatus = 'approved' "
                    + "AND c.createdAt < :cursor ORDER BY c.createdAt DESC")
    List<Comment> findTopLevelBefore(
            @Param("postId") UUID postId,
            @Param("cursor") OffsetDateTime cursor,
            Pageable pageable);

    /**
     * First keyset page of approved direct replies to a parent comment, newest first.
     *
     * @param parentId parent comment whose direct replies are listed
     * @param pageable page size carrier
     * @return approved direct replies ordered by {@code created_at} descending
     */
    @Query(
            "SELECT c FROM Comment c WHERE c.parentId = :parentId "
                    + "AND c.moderationStatus = 'approved' "
                    + "ORDER BY c.createdAt DESC")
    List<Comment> findFirstReplies(@Param("parentId") UUID parentId, Pageable pageable);

    /**
     * Keyset page of approved direct replies older than the cursor, newest first.
     *
     * @param parentId parent comment whose direct replies are listed
     * @param cursor exclusive upper bound on {@code created_at}; never null
     * @param pageable page size carrier
     * @return approved direct replies ordered by {@code created_at} descending
     */
    @Query(
            "SELECT c FROM Comment c WHERE c.parentId = :parentId "
                    + "AND c.moderationStatus = 'approved' "
                    + "AND c.createdAt < :cursor ORDER BY c.createdAt DESC")
    List<Comment> findRepliesBefore(
            @Param("parentId") UUID parentId,
            @Param("cursor") OffsetDateTime cursor,
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
