package com.app.modules.social.repository;

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
     * Deletes a block edge, returning the affected-row count so the caller can distinguish an
     * actual unblock from a no-op instead of loading the row first and calling {@code
     * delete(entity)}, which raises {@link
     * org.springframework.orm.ObjectOptimisticLockingFailureException} when a concurrent request
     * already removed the same row.
     *
     * @param blockerId the account that issued the block
     * @param blockedId the blocked account
     * @return number of rows deleted (0 or 1)
     */
    @Modifying
    @Query(
            "DELETE FROM Block b WHERE b.id.blockerId = :blockerId "
                    + "AND b.id.blockedId = :blockedId")
    int deleteByBlockerIdAndBlockedId(
            @Param("blockerId") UUID blockerId, @Param("blockedId") UUID blockedId);

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
     * The subset of {@code userIds} the viewer has outgoing-blocked, in one round trip.
     *
     * <p>Deliberately outgoing-only: the application implements a stealth block model, so no
     * response surface ever renders "this user has blocked the viewer" - only "the viewer has
     * blocked this user" ({@code isBlocking}) is a legitimate thing to tell the viewer about
     * themselves. An incoming-block query has no remaining caller.
     *
     * @param viewerId the requesting viewer
     * @param userIds candidate user ids on the current page
     * @return the ids among {@code userIds} the viewer blocks; absence means not blocked by the
     *     viewer
     */
    @Query(
            value =
                    "SELECT blocked_id FROM blocks WHERE blocker_id = :viewerId "
                            + "AND blocked_id IN (:userIds)",
            nativeQuery = true)
    List<UUID> findOutgoingBlockedIds(
            @Param("viewerId") UUID viewerId, @Param("userIds") Collection<UUID> userIds);

    /**
     * Every id in a block relationship with {@code userId}, in either direction, in one round trip.
     *
     * <p>Used to filter WebSocket fan-out: a broadcast authored by (or otherwise attributable to)
     * {@code userId} must not reach a subscriber who blocks or is blocked by them. One query per
     * fan-out event regardless of subscriber count, bounded by {@code userId}'s own block-list size
     * rather than the number of connected sessions.
     *
     * @param userId the content owner whose block relationships are resolved
     * @return every counterparty id blocking or blocked by {@code userId}
     */
    @Query(
            value =
                    "SELECT blocked_id AS id FROM blocks WHERE blocker_id = :userId "
                            + "UNION "
                            + "SELECT blocker_id AS id FROM blocks WHERE blocked_id = :userId",
            nativeQuery = true)
    List<UUID> findBlockedCounterpartyIds(@Param("userId") UUID userId);
}
