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
     * First keyset page of approved top-level comments for a post, newest first, excluding any
     * commenter in a block relationship with the viewer.
     *
     * <p>Paired with {@link #findTopLevelBefore}; the no-cursor variant avoids binding an untyped
     * null timestamp.
     *
     * @param postId post whose comments are listed
     * @param viewerId the requesting viewer; commenters in a block relationship with this user are
     *     excluded
     * @param pageable page size carrier (page number is always 0 for keyset paging)
     * @return top-level approved comments ordered by the {@code (created_at, id)} tuple descending
     */
    @Query(
            value =
                    "SELECT * FROM comments WHERE post_id = :postId AND parent_id IS NULL "
                            + "AND moderation_status = 'approved' AND deleted_at IS NULL "
                            + "AND NOT EXISTS (SELECT 1 FROM blocks b"
                            + " WHERE (b.blocker_id = :viewerId AND b.blocked_id = comments.user_id)"
                            + " OR (b.blocker_id = comments.user_id AND b.blocked_id = :viewerId)) "
                            + "ORDER BY created_at DESC, id DESC",
            nativeQuery = true)
    List<Comment> findFirstTopLevel(
            @Param("postId") UUID postId, @Param("viewerId") UUID viewerId, Pageable pageable);

    /**
     * Most-liked approved top-level comments for a post, for the pinned first-page block, excluding
     * any commenter in a block relationship with the viewer.
     *
     * <p>Only comments with at least one like are eligible: a zero-like comment carries no
     * popularity signal, and without the threshold every post with no likes would pin its three
     * newest comments and duplicate the head of the newest-first body. Served exactly by {@code
     * idx_comments_post_top_liked} (V41), which makes this an index seek with no sort.
     *
     * @param postId post whose comments are ranked
     * @param viewerId the requesting viewer; commenters in a block relationship with this user are
     *     excluded
     * @param pageable page size carrier, bound to the pinned-block size by the caller
     * @return eligible top-level comments ordered by {@code (like_count, created_at, id)}
     *     descending
     */
    @Query(
            value =
                    "SELECT * FROM comments WHERE post_id = :postId AND parent_id IS NULL "
                            + "AND moderation_status = 'approved' AND deleted_at IS NULL "
                            + "AND like_count > 0 "
                            + "AND NOT EXISTS (SELECT 1 FROM blocks b"
                            + " WHERE (b.blocker_id = :viewerId AND b.blocked_id = comments.user_id)"
                            + " OR (b.blocker_id = comments.user_id AND b.blocked_id = :viewerId)) "
                            + "ORDER BY like_count DESC, created_at DESC, id DESC",
            nativeQuery = true)
    List<Comment> findTopLikedTopLevel(
            @Param("postId") UUID postId, @Param("viewerId") UUID viewerId, Pageable pageable);

    /**
     * First keyset page of approved top-level comments, excluding the already-pinned comments and
     * any commenter in a block relationship with the viewer.
     *
     * <p>The pinned exclusion is applied in SQL rather than in the caller so that {@code LIMIT}
     * counts post-filter rows and the body still yields a full page. Excluding in Java would
     * silently shorten the page by the number of pinned comments it happened to contain.
     *
     * @param postId post whose comments are listed
     * @param excludedIds ids already returned in the pinned block; empty excludes nothing
     * @param viewerId the requesting viewer; commenters in a block relationship with this user are
     *     excluded
     * @param pageable page size carrier
     * @return top-level approved comments ordered by the {@code (created_at, id)} tuple descending
     */
    @Query(
            value =
                    "SELECT * FROM comments WHERE post_id = :postId AND parent_id IS NULL "
                            + "AND moderation_status = 'approved' AND deleted_at IS NULL "
                            + "AND id <> ALL(CAST(:excludedIds AS uuid[])) "
                            + "AND NOT EXISTS (SELECT 1 FROM blocks b"
                            + " WHERE (b.blocker_id = :viewerId AND b.blocked_id = comments.user_id)"
                            + " OR (b.blocker_id = comments.user_id AND b.blocked_id = :viewerId)) "
                            + "ORDER BY created_at DESC, id DESC",
            nativeQuery = true)
    List<Comment> findFirstTopLevelExcluding(
            @Param("postId") UUID postId,
            @Param("excludedIds") UUID[] excludedIds,
            @Param("viewerId") UUID viewerId,
            Pageable pageable);

    /**
     * Keyset page of approved top-level comments strictly after the cursor tuple, newest first,
     * excluding any commenter in a block relationship with the viewer.
     *
     * <p>The {@code (created_at, id)} row-value comparison seeks directly to the cursor position
     * and never drops comments sharing a boundary {@code created_at}. Served exactly by {@code
     * idx_comments_post_root_id} (V36).
     *
     * @param postId post whose comments are listed
     * @param viewerId the requesting viewer; commenters in a block relationship with this user are
     *     excluded
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
                            + "AND NOT EXISTS (SELECT 1 FROM blocks b"
                            + " WHERE (b.blocker_id = :viewerId AND b.blocked_id = comments.user_id)"
                            + " OR (b.blocker_id = comments.user_id AND b.blocked_id = :viewerId)) "
                            + "ORDER BY created_at DESC, id DESC",
            nativeQuery = true)
    List<Comment> findTopLevelBefore(
            @Param("postId") UUID postId,
            @Param("viewerId") UUID viewerId,
            @Param("cursorTime") OffsetDateTime cursorTime,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);

    /**
     * First keyset page of approved direct replies to a parent comment, newest first, excluding any
     * commenter in a block relationship with the viewer.
     *
     * @param parentId parent comment whose direct replies are listed
     * @param viewerId the requesting viewer; commenters in a block relationship with this user are
     *     excluded
     * @param pageable page size carrier
     * @return approved direct replies ordered by the {@code (created_at, id)} tuple descending
     */
    @Query(
            value =
                    "SELECT * FROM comments WHERE parent_id = :parentId "
                            + "AND moderation_status = 'approved' AND deleted_at IS NULL "
                            + "AND NOT EXISTS (SELECT 1 FROM blocks b"
                            + " WHERE (b.blocker_id = :viewerId AND b.blocked_id = comments.user_id)"
                            + " OR (b.blocker_id = comments.user_id AND b.blocked_id = :viewerId)) "
                            + "ORDER BY created_at DESC, id DESC",
            nativeQuery = true)
    List<Comment> findFirstReplies(
            @Param("parentId") UUID parentId, @Param("viewerId") UUID viewerId, Pageable pageable);

    /**
     * Keyset page of approved direct replies strictly after the cursor tuple, newest first,
     * excluding any commenter in a block relationship with the viewer.
     *
     * <p>The {@code (created_at, id)} row-value comparison seeks directly to the cursor position
     * and never drops replies sharing a boundary {@code created_at}. Served exactly by {@code
     * idx_comments_parent_id} (V36).
     *
     * @param parentId parent comment whose direct replies are listed
     * @param viewerId the requesting viewer; commenters in a block relationship with this user are
     *     excluded
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
                            + "AND NOT EXISTS (SELECT 1 FROM blocks b"
                            + " WHERE (b.blocker_id = :viewerId AND b.blocked_id = comments.user_id)"
                            + " OR (b.blocker_id = comments.user_id AND b.blocked_id = :viewerId)) "
                            + "ORDER BY created_at DESC, id DESC",
            nativeQuery = true)
    List<Comment> findRepliesBefore(
            @Param("parentId") UUID parentId,
            @Param("viewerId") UUID viewerId,
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

    /**
     * Counts the comments a {@link #softDeleteSubtree} call on the same target would soft-delete.
     *
     * <p>Returns the target plus every descendant at any depth, excluding rows already soft-deleted
     * - the same set the delete's final predicate keeps. Like the delete, the walk itself does not
     * filter on {@code deleted_at}, so a live comment restored beneath a still-deleted parent is
     * reached rather than cut off.
     *
     * <p>The search space is bounded to the target's own thread before the walk begins: every
     * descendant carries the top-level ancestor's id in {@code root_id}, so {@code
     * idx_comments_root} yields the candidate rows in one index scan and the recursion runs over
     * that materialised set rather than over {@code comments}. Recursion is additionally capped at
     * eleven levels by {@code CHECK (depth BETWEEN 0 AND 10)}.
     *
     * @param commentId root of the subtree to measure
     * @return number of comments that are not already soft-deleted in that subtree; zero when the
     *     comment does not exist
     */
    @Query(
            value =
                    """
					WITH RECURSIVE anchor AS (
						SELECT id, coalesce(root_id, id) AS thread_id
						FROM comments WHERE id = :commentId
					),
					thread AS (
						SELECT c.id, c.parent_id, c.deleted_at
						FROM comments c JOIN anchor a ON c.id = a.thread_id
						UNION ALL
						SELECT c.id, c.parent_id, c.deleted_at
						FROM comments c JOIN anchor a ON c.root_id = a.thread_id
					),
					subtree AS (
						SELECT t.id, t.deleted_at FROM thread t JOIN anchor a ON t.id = a.id
						UNION ALL
						SELECT t.id, t.deleted_at
						FROM thread t JOIN subtree s ON t.parent_id = s.id
					)
					SELECT count(*) FROM subtree WHERE deleted_at IS NULL
					""",
            nativeQuery = true)
    int countSubtree(@Param("commentId") UUID commentId);
}
