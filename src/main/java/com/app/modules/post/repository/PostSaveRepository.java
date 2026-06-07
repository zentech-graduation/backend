package com.app.modules.post.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.modules.post.entity.PostSave;
import com.app.modules.post.entity.PostSaveId;

@Repository
public interface PostSaveRepository extends JpaRepository<PostSave, PostSaveId> {

    /**
     * Keyset page of a user's saves, newest first.
     *
     * @param userId saving user
     * @param cursor exclusive upper bound on {@code created_at}; {@code null} for the first page
     * @param pageable page size carrier
     * @return saves ordered by {@code created_at} descending
     */
    @Query(
            "SELECT ps FROM PostSave ps WHERE ps.id.userId = :userId "
                    + "AND (:cursor IS NULL OR ps.createdAt < :cursor) "
                    + "ORDER BY ps.createdAt DESC")
    List<PostSave> findSavesWithCursor(
            @Param("userId") UUID userId,
            @Param("cursor") OffsetDateTime cursor,
            Pageable pageable);
}
