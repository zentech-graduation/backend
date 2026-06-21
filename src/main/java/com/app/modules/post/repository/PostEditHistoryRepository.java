package com.app.modules.post.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.modules.post.entity.PostEditHistory;

@Repository
public interface PostEditHistoryRepository extends JpaRepository<PostEditHistory, UUID> {

    /**
     * First keyset page of a post's caption edit history, newest first.
     *
     * <p>Served by {@code idx_post_edit_history_post_edited} (V22). Paired with {@link
     * #findByPostBefore}; the no-cursor variant avoids binding an untyped null timestamp, which
     * PostgreSQL cannot type-infer.
     *
     * @param postId edited post
     * @param pageable page size carrier
     * @return history rows ordered by {@code edited_at} descending
     */
    @Query("SELECT h FROM PostEditHistory h WHERE h.postId = :postId ORDER BY h.editedAt DESC")
    List<PostEditHistory> findFirstByPost(@Param("postId") UUID postId, Pageable pageable);

    /**
     * Keyset page of a post's caption edit history older than the cursor, newest first.
     *
     * <p>Served by {@code idx_post_edit_history_post_edited} (V22).
     *
     * @param postId edited post
     * @param cursor exclusive upper bound on {@code edited_at}; never null
     * @param pageable page size carrier
     * @return history rows ordered by {@code edited_at} descending
     */
    @Query(
            "SELECT h FROM PostEditHistory h WHERE h.postId = :postId "
                    + "AND h.editedAt < :cursor "
                    + "ORDER BY h.editedAt DESC")
    List<PostEditHistory> findByPostBefore(
            @Param("postId") UUID postId,
            @Param("cursor") OffsetDateTime cursor,
            Pageable pageable);
}
