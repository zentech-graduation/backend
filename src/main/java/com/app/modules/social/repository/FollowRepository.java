package com.app.modules.social.repository;

import java.time.OffsetDateTime;
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

    @Query(
            "SELECT f FROM Follow f WHERE f.id.followingId = :userId "
                    + "AND f.status = :status "
                    + "AND f.createdAt < :cursor "
                    + "AND f.id.followerId NOT IN (SELECT b.id.blockedId FROM Block b WHERE b.id.blockerId = :currentUserId) "
                    + "AND f.id.followerId NOT IN (SELECT b.id.blockerId FROM Block b WHERE b.id.blockedId = :currentUserId) "
                    + "ORDER BY f.createdAt DESC")
    List<Follow> findFollowersWithCursor(
            @Param("userId") UUID userId,
            @Param("currentUserId") UUID currentUserId,
            @Param("status") FollowStatus status,
            @Param("cursor") OffsetDateTime cursor,
            Pageable pageable);

    @Query(
            "SELECT f FROM Follow f WHERE f.id.followerId = :userId "
                    + "AND f.status = :status "
                    + "AND f.createdAt < :cursor "
                    + "AND f.id.followingId NOT IN (SELECT b.id.blockedId FROM Block b WHERE b.id.blockerId = :currentUserId) "
                    + "AND f.id.followingId NOT IN (SELECT b.id.blockerId FROM Block b WHERE b.id.blockedId = :currentUserId) "
                    + "ORDER BY f.createdAt DESC")
    List<Follow> findFollowingWithCursor(
            @Param("userId") UUID userId,
            @Param("currentUserId") UUID currentUserId,
            @Param("status") FollowStatus status,
            @Param("cursor") OffsetDateTime cursor,
            Pageable pageable);
}
