package com.app.modules.post.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.modules.post.entity.Post;
import com.app.modules.post.enums.PostStatus;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

    Optional<Post> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Loads posts by id with their media collections fetch-joined.
     *
     * <p>Callers that consume {@code Post.media} outside an open persistence session (detached
     * hydration in ranking pipelines) must use this instead of {@code findAllById}.
     *
     * @param ids post identifiers
     * @return matching non-deleted posts in no particular order
     */
    @Query("SELECT DISTINCT p FROM Post p LEFT JOIN FETCH p.media WHERE p.id IN :ids")
    List<Post> findAllWithMediaByIdIn(@Param("ids") Collection<UUID> ids);

    /**
     * Reads the author of a post regardless of its soft-delete state.
     *
     * @param postId post identifier
     * @return author identifier when the post exists
     */
    @Query(value = "SELECT user_id FROM posts WHERE id = :postId", nativeQuery = true)
    Optional<UUID> findOwnerIdIncludingDeleted(@Param("postId") UUID postId);

    /**
     * Reads the persisted status of a post regardless of its soft-delete state.
     *
     * @param postId post identifier
     * @return lowercase PostgreSQL status when the post exists
     */
    @Query(value = "SELECT status::text FROM posts WHERE id = :postId", nativeQuery = true)
    Optional<String> findStatusIncludingDeleted(@Param("postId") UUID postId);

    /**
     * Applies an administrator-controlled status and soft-delete state to a post.
     *
     * @param postId post identifier
     * @param status lowercase PostgreSQL post status
     * @param deletedAt soft-delete timestamp, or null when restoring
     * @return number of updated posts
     */
    @Modifying
    @Query(
            value =
                    "UPDATE posts SET status = CAST(:status AS post_status), "
                            + "deleted_at = :deletedAt WHERE id = :postId",
            nativeQuery = true)
    int applyAdminModeration(
            @Param("postId") UUID postId,
            @Param("status") String status,
            @Param("deletedAt") OffsetDateTime deletedAt);

    /**
     * First keyset page of a user's posts in the given status, newest first.
     *
     * <p>Paired with {@link #findUserPostsBefore}; the no-cursor variant avoids binding an untyped
     * null timestamp, which PostgreSQL cannot type-infer.
     *
     * @param userId post author
     * @param status status filter; listing endpoints page published posts only
     * @param pageable page size carrier (page number is always 0 for keyset paging)
     * @return posts ordered by {@code created_at} descending
     */
    @Query(
            "SELECT p FROM Post p WHERE p.userId = :userId "
                    + "AND p.status = :status "
                    + "ORDER BY p.createdAt DESC")
    List<Post> findFirstUserPosts(
            @Param("userId") UUID userId, @Param("status") PostStatus status, Pageable pageable);

    /**
     * Keyset page of a user's posts older than the cursor, newest first.
     *
     * @param userId post author
     * @param status status filter; listing endpoints page published posts only
     * @param cursor exclusive upper bound on {@code created_at}; never null
     * @param pageable page size carrier
     * @return posts ordered by {@code created_at} descending
     */
    @Query(
            "SELECT p FROM Post p WHERE p.userId = :userId "
                    + "AND p.status = :status "
                    + "AND p.createdAt < :cursor "
                    + "ORDER BY p.createdAt DESC")
    List<Post> findUserPostsBefore(
            @Param("userId") UUID userId,
            @Param("status") PostStatus status,
            @Param("cursor") OffsetDateTime cursor,
            Pageable pageable);

    /**
     * First keyset batch of posts in the given status across all users, newest first.
     *
     * <p>Used by the Elasticsearch index seed runner to start walking the table.
     *
     * @param status status filter; the seed runner indexes published posts only
     * @param pageable batch size carrier
     * @return posts ordered by {@code created_at} descending
     */
    @Query("SELECT p FROM Post p WHERE p.status = :status ORDER BY p.createdAt DESC")
    List<Post> findFirstPublished(@Param("status") PostStatus status, Pageable pageable);

    /**
     * Keyset batch of posts in the given status older than the cursor, newest first.
     *
     * @param status status filter; the seed runner indexes published posts only
     * @param cursor exclusive upper bound on {@code created_at}; never null
     * @param pageable batch size carrier
     * @return posts ordered by {@code created_at} descending
     */
    @Query(
            "SELECT p FROM Post p WHERE p.status = :status "
                    + "AND p.createdAt < :cursor "
                    + "ORDER BY p.createdAt DESC")
    List<Post> findPublishedBefore(
            @Param("status") PostStatus status,
            @Param("cursor") OffsetDateTime cursor,
            Pageable pageable);

    /**
     * First keyset page of published posts authored by any user in {@code authorIds}, newest first.
     *
     * <p>Paired with {@link #findFeedPostsBefore}; the no-cursor variant avoids binding an untyped
     * null timestamp, which PostgreSQL cannot type-infer.
     *
     * @param authorIds post authors eligible to appear in the feed; must not be empty
     * @param status status filter; feed endpoints page published posts only
     * @param pageable page size carrier (page number is always 0 for keyset paging)
     * @return posts ordered by {@code created_at} descending
     */
    @Query(
            "SELECT p FROM Post p WHERE p.userId IN :authorIds"
                    + " AND p.status = :status"
                    + " ORDER BY p.createdAt DESC")
    List<Post> findFirstFeedPosts(
            @Param("authorIds") List<UUID> authorIds,
            @Param("status") PostStatus status,
            Pageable pageable);

    /**
     * Keyset page of published posts authored by any user in {@code authorIds}, older than the
     * cursor, newest first.
     *
     * @param authorIds post authors eligible to appear in the feed; must not be empty
     * @param status status filter; feed endpoints page published posts only
     * @param cursor exclusive upper bound on {@code created_at}; never null
     * @param pageable page size carrier
     * @return posts ordered by {@code created_at} descending
     */
    @Query(
            "SELECT p FROM Post p WHERE p.userId IN :authorIds"
                    + " AND p.status = :status"
                    + " AND p.createdAt < :cursor"
                    + " ORDER BY p.createdAt DESC")
    List<Post> findFeedPostsBefore(
            @Param("authorIds") List<UUID> authorIds,
            @Param("status") PostStatus status,
            @Param("cursor") OffsetDateTime cursor,
            Pageable pageable);

    /**
     * Reads the trigger-maintained like counter directly from the database.
     *
     * <p>Bypasses the first-level-cached entity state, which goes stale once {@code
     * trg_post_like_count} fires.
     *
     * @param postId post whose counter is read; the post must exist
     * @return current {@code like_count} value
     */
    @Query("SELECT p.likeCount FROM Post p WHERE p.id = :postId")
    int findLikeCount(@Param("postId") UUID postId);
}
