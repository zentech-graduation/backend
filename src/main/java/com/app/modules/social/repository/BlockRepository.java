package com.app.modules.social.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.modules.social.entity.Block;
import com.app.modules.social.entity.BlockId;

@Repository
public interface BlockRepository extends JpaRepository<Block, BlockId> {

    Optional<Block> findById(BlockId id);

    boolean existsById(BlockId id);

    List<Block> findByIdBlockerId(UUID blockerId);

    List<Block> findByIdBlockedId(UUID blockedId);

    default boolean existsBetween(UUID firstUserId, UUID secondUserId) {
        return existsById(new BlockId(firstUserId, secondUserId))
                || existsById(new BlockId(secondUserId, firstUserId));
    }

    /**
     * First keyset page of the viewer's outgoing blocks, newest first.
     *
     * <p>Native so the paired {@code before} query can use a row-value tuple comparison for an
     * exact index seek. The tiebreaker is {@code blocked_id}, the unique key component within a
     * blocker. Served by {@code idx_blocks_blocker_created_blocked} (V40): {@code blocker_id} is
     * equality-matched, leaving {@code (created_at, blocked_id)} as a pure index range scan.
     *
     * @param blockerId the viewer whose outgoing blocks are listed
     * @param pageable page size carrier
     * @return blocks ordered by the {@code (created_at, blocked_id)} tuple descending
     */
    @Query(
            value =
                    "SELECT * FROM blocks WHERE blocker_id = :blockerId "
                            + "ORDER BY created_at DESC, blocked_id DESC",
            nativeQuery = true)
    List<Block> findFirstBlocked(@Param("blockerId") UUID blockerId, Pageable pageable);

    /**
     * Keyset page of the viewer's outgoing blocks strictly after the cursor tuple, newest first.
     *
     * <p>The {@code (created_at, blocked_id)} row-value comparison seeks directly to the cursor
     * position and never drops rows sharing a boundary {@code created_at}. Served exactly by {@code
     * idx_blocks_blocker_created_blocked} (V40).
     *
     * @param blockerId the viewer whose outgoing blocks are listed
     * @param cursorTime {@code created_at} of the cursor row; never null
     * @param cursorBlockedId blocked-user id of the cursor row, breaking ties on equal {@code
     *     created_at}
     * @param pageable page size carrier
     * @return blocks ordered by the {@code (created_at, blocked_id)} tuple descending
     */
    @Query(
            value =
                    "SELECT * FROM blocks WHERE blocker_id = :blockerId "
                            + "AND (created_at, blocked_id) < (:cursorTime, :cursorBlockedId) "
                            + "ORDER BY created_at DESC, blocked_id DESC",
            nativeQuery = true)
    List<Block> findBlockedBefore(
            @Param("blockerId") UUID blockerId,
            @Param("cursorTime") OffsetDateTime cursorTime,
            @Param("cursorBlockedId") UUID cursorBlockedId,
            Pageable pageable);

    /**
     * Every block edge between the viewer and any of {@code userIds}, in either direction, in one
     * round trip.
     *
     * <p>Compiles to two independent index scans against {@code blocks_pkey} and {@code
     * idx_blocks_blocked} appended in a single statement, one for each direction.
     *
     * @param viewerId the requesting viewer
     * @param userIds candidate user ids on the current page
     * @return directed edges; a user absent from the result has no block relationship with the
     *     viewer in either direction
     */
    @Query(
            value =
                    "SELECT blocked_id AS other_id, true AS outgoing FROM blocks"
                            + " WHERE blocker_id = :viewerId AND blocked_id IN (:userIds)"
                            + " UNION ALL "
                            + "SELECT blocker_id AS other_id, false AS outgoing FROM blocks"
                            + " WHERE blocked_id = :viewerId AND blocker_id IN (:userIds)",
            nativeQuery = true)
    List<BlockEdgeProjection> findRelationshipEdges(
            @Param("viewerId") UUID viewerId, @Param("userIds") Collection<UUID> userIds);
}
