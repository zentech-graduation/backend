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

import com.app.modules.social.entity.Follow;
import com.app.modules.social.entity.FollowId;
import com.app.modules.social.enums.FollowStatus;

@Repository
public interface FollowRepository extends JpaRepository<Follow, FollowId>, FollowRepositoryCustom {

    Optional<Follow> findByIdAndStatus(FollowId id, FollowStatus status);

    Optional<Follow> findById(FollowId id);

    boolean existsById(FollowId id);

    boolean existsByIdAndStatus(FollowId id, FollowStatus status);

    List<Follow> findByIdFollowerIdAndStatus(UUID followerId, FollowStatus status);

    /**
     * Returns the IDs of all users that the given viewer follows with the specified status.
     *
     * <p>Used by the feed to resolve the accepted-follow author list before applying block
     * exclusions. Projects only the {@code following_id} column to avoid loading full entity graphs
     * when only identifiers are needed.
     *
     * @param viewerId the follower whose outgoing follows are queried
     * @param status follow status filter; pass {@link FollowStatus#ACCEPTED} for feed resolution
     * @return list of user IDs being followed with the given status
     */
    @Query(
            "SELECT f.id.followingId FROM Follow f"
                    + " WHERE f.id.followerId = :viewerId AND f.status = :status")
    List<UUID> findAcceptedFollowingIds(
            @Param("viewerId") UUID viewerId, @Param("status") FollowStatus status);

    List<Follow> findByIdFollowingIdAndStatus(UUID followingId, FollowStatus status);

    List<Follow> findByIdFollowingIdAndStatusOrderByCreatedAtDesc(
            UUID followingId, FollowStatus status);

    /**
     * First keyset page of a user's accepted followers, excluding block relationships, newest
     * first.
     *
     * <p>Native so the paired {@code before} query can use a row-value tuple comparison for an
     * exact index seek. The tiebreaker is {@code follower_id}, the unique follower key within a
     * followee.
     *
     * @param userId followee whose followers are listed
     * @param currentUserId viewer, whose block relationships filter the result
     * @param pageable page size carrier
     * @return accepted followers ordered by the {@code (created_at, follower_id)} tuple descending
     */
    @Query(
            value =
                    "SELECT * FROM follows WHERE following_id = :userId AND status = 'accepted' "
                            + "AND follower_id NOT IN (SELECT blocked_id FROM blocks WHERE blocker_id ="
                            + " :currentUserId) "
                            + "AND follower_id NOT IN (SELECT blocker_id FROM blocks WHERE blocked_id ="
                            + " :currentUserId) "
                            + "ORDER BY created_at DESC, follower_id DESC",
            nativeQuery = true)
    List<Follow> findFirstFollowers(
            @Param("userId") UUID userId,
            @Param("currentUserId") UUID currentUserId,
            Pageable pageable);

    /**
     * Keyset page of a user's accepted followers strictly after the cursor tuple, newest first.
     *
     * <p>The {@code (created_at, follower_id)} row-value comparison seeks directly to the cursor
     * position and never drops followers sharing a boundary {@code created_at}. Served exactly by
     * {@code idx_follows_following_created_follower} (V37).
     *
     * @param userId followee whose followers are listed
     * @param currentUserId viewer, whose block relationships filter the result
     * @param cursorTime {@code created_at} of the cursor row; never null
     * @param cursorFollowerId follower id of the cursor row, breaking ties on equal {@code
     *     created_at}
     * @param pageable page size carrier
     * @return accepted followers ordered by the {@code (created_at, follower_id)} tuple descending
     */
    @Query(
            value =
                    "SELECT * FROM follows WHERE following_id = :userId AND status = 'accepted' "
                            + "AND (created_at, follower_id) < (:cursorTime, :cursorFollowerId) "
                            + "AND follower_id NOT IN (SELECT blocked_id FROM blocks WHERE blocker_id ="
                            + " :currentUserId) "
                            + "AND follower_id NOT IN (SELECT blocker_id FROM blocks WHERE blocked_id ="
                            + " :currentUserId) "
                            + "ORDER BY created_at DESC, follower_id DESC",
            nativeQuery = true)
    List<Follow> findFollowersBefore(
            @Param("userId") UUID userId,
            @Param("currentUserId") UUID currentUserId,
            @Param("cursorTime") OffsetDateTime cursorTime,
            @Param("cursorFollowerId") UUID cursorFollowerId,
            Pageable pageable);

    /**
     * First keyset page of the users a user follows, excluding block relationships, newest first.
     *
     * <p>Native so the paired {@code before} query can use a row-value tuple comparison for an
     * exact index seek. The tiebreaker is {@code following_id}, the unique followee key within a
     * follower.
     *
     * @param userId follower whose followees are listed
     * @param currentUserId viewer, whose block relationships filter the result
     * @param pageable page size carrier
     * @return accepted followees ordered by the {@code (created_at, following_id)} tuple descending
     */
    @Query(
            value =
                    "SELECT * FROM follows WHERE follower_id = :userId AND status = 'accepted' "
                            + "AND following_id NOT IN (SELECT blocked_id FROM blocks WHERE blocker_id ="
                            + " :currentUserId) "
                            + "AND following_id NOT IN (SELECT blocker_id FROM blocks WHERE blocked_id ="
                            + " :currentUserId) "
                            + "ORDER BY created_at DESC, following_id DESC",
            nativeQuery = true)
    List<Follow> findFirstFollowing(
            @Param("userId") UUID userId,
            @Param("currentUserId") UUID currentUserId,
            Pageable pageable);

    /**
     * Keyset page of the users a user follows strictly after the cursor tuple, newest first.
     *
     * <p>The {@code (created_at, following_id)} row-value comparison seeks directly to the cursor
     * position and never drops followees sharing a boundary {@code created_at}. Served exactly by
     * {@code idx_follows_follower_created_following} (V37).
     *
     * @param userId follower whose followees are listed
     * @param currentUserId viewer, whose block relationships filter the result
     * @param cursorTime {@code created_at} of the cursor row; never null
     * @param cursorFollowingId followee id of the cursor row, breaking ties on equal {@code
     *     created_at}
     * @param pageable page size carrier
     * @return accepted followees ordered by the {@code (created_at, following_id)} tuple descending
     */
    @Query(
            value =
                    "SELECT * FROM follows WHERE follower_id = :userId AND status = 'accepted' "
                            + "AND (created_at, following_id) < (:cursorTime, :cursorFollowingId) "
                            + "AND following_id NOT IN (SELECT blocked_id FROM blocks WHERE blocker_id ="
                            + " :currentUserId) "
                            + "AND following_id NOT IN (SELECT blocker_id FROM blocks WHERE blocked_id ="
                            + " :currentUserId) "
                            + "ORDER BY created_at DESC, following_id DESC",
            nativeQuery = true)
    List<Follow> findFollowingBefore(
            @Param("userId") UUID userId,
            @Param("currentUserId") UUID currentUserId,
            @Param("cursorTime") OffsetDateTime cursorTime,
            @Param("cursorFollowingId") UUID cursorFollowingId,
            Pageable pageable);

    /**
     * Every follow edge between the viewer and any of {@code userIds}, in either direction, in one
     * round trip.
     *
     * <p>Compiles to two independent index scans against {@code
     * idx_follows_follower_created_following} and {@code idx_follows_following_created_follower}
     * appended in a single statement, one for each direction.
     *
     * @param viewerId the requesting viewer
     * @param userIds candidate user ids on the current page
     * @return directed edges with their status; a user absent from the result has no follow
     *     relationship with the viewer in either direction
     */
    @Query(
            value =
                    "SELECT following_id AS other_id, status, true AS outgoing FROM follows"
                            + " WHERE follower_id = :viewerId AND following_id IN (:userIds)"
                            + " UNION ALL "
                            + "SELECT follower_id AS other_id, status, false AS outgoing FROM follows"
                            + " WHERE following_id = :viewerId AND follower_id IN (:userIds)",
            nativeQuery = true)
    List<FollowEdgeProjection> findRelationshipEdges(
            @Param("viewerId") UUID viewerId, @Param("userIds") Collection<UUID> userIds);
}
