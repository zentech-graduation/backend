package com.app.modules.social.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
