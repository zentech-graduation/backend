package com.app.modules.post.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.modules.post.entity.Post;
import com.app.modules.post.enums.PostStatus;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

    Optional<Post> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Keyset page of a user's posts in the given status, newest first.
     *
     * @param userId post author
     * @param status status filter; listing endpoints page published posts only
     * @param cursor exclusive upper bound on {@code created_at}; {@code null} for the first page
     * @param pageable page size carrier (page number is always 0 for keyset paging)
     * @return posts ordered by {@code created_at} descending
     */
    @Query(
            "SELECT p FROM Post p WHERE p.userId = :userId "
                    + "AND p.status = :status "
                    + "AND (:cursor IS NULL OR p.createdAt < :cursor) "
                    + "ORDER BY p.createdAt DESC")
    List<Post> findUserPostsWithCursor(
            @Param("userId") UUID userId,
            @Param("status") PostStatus status,
            @Param("cursor") OffsetDateTime cursor,
            Pageable pageable);

    /**
     * Keyset batch of posts in the given status across all users, newest first.
     *
     * <p>Used by the Elasticsearch index seed runner to walk the table in fixed-size batches.
     *
     * @param status status filter; the seed runner indexes published posts only
     * @param cursor exclusive upper bound on {@code created_at}; {@code null} for the first batch
     * @param pageable batch size carrier
     * @return posts ordered by {@code created_at} descending
     */
    @Query(
            "SELECT p FROM Post p WHERE p.status = :status "
                    + "AND (:cursor IS NULL OR p.createdAt < :cursor) "
                    + "ORDER BY p.createdAt DESC")
    List<Post> findPublishedWithCursor(
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
