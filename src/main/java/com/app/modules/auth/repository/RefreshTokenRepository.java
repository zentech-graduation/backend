package com.app.modules.auth.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.modules.auth.entity.RefreshToken;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHashAndRevokedAtIsNull(String tokenHash);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revokes every active refresh token belonging to the given user.
     *
     * @param userId user whose tokens should be revoked
     * @param now revocation timestamp to record
     * @return number of sessions that were active and are now revoked
     */
    @Modifying
    @Query(
            "UPDATE RefreshToken r SET r.revokedAt = :now "
                    + "WHERE r.userId = :userId AND r.revokedAt IS NULL")
    int revokeAllActiveByUserId(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);

    /**
     * Lists a user's live refresh tokens, newest first.
     *
     * <p>Live means neither revoked nor past expiry. Served by {@code idx_refresh_tokens_user}
     * (V15).
     *
     * @param userId owner whose sessions to list
     * @param now comparison instant for the expiry check
     * @param limit maximum rows to return
     * @return live refresh tokens ordered by issuance time descending
     */
    @Query(
            "SELECT r FROM RefreshToken r "
                    + "WHERE r.userId = :userId AND r.revokedAt IS NULL AND r.expiresAt > :now "
                    + "ORDER BY r.createdAt DESC, r.id DESC LIMIT :limit")
    List<RefreshToken> findActiveByUserId(
            @Param("userId") UUID userId,
            @Param("now") OffsetDateTime now,
            @Param("limit") int limit);

    /**
     * Revokes a single active refresh token by its hash and returns the row count affected.
     * Returning the count lets callers detect a concurrent rotation losing the race so that replay
     * can be flagged.
     *
     * @param tokenHash SHA-256 hash of the raw refresh token
     * @param now revocation timestamp to record
     * @return number of rows updated; {@code 0} when the token does not exist or was already
     *     revoked
     */
    @Modifying
    @Query(
            "UPDATE RefreshToken r SET r.revokedAt = :now "
                    + "WHERE r.tokenHash = :tokenHash AND r.revokedAt IS NULL")
    int revokeByTokenHash(@Param("tokenHash") String tokenHash, @Param("now") OffsetDateTime now);

    /**
     * Deletes all refresh tokens that are either expired past the grace period or revoked past the
     * retention window.
     *
     * @param expiredBefore delete rows where {@code expires_at < expiredBefore}
     * @param revokedBefore delete rows where {@code revoked_at < revokedBefore}
     * @return number of rows deleted
     */
    @Modifying
    @Query(
            "DELETE FROM RefreshToken r "
                    + "WHERE r.expiresAt < :expiredBefore "
                    + "OR (r.revokedAt IS NOT NULL AND r.revokedAt < :revokedBefore)")
    int deleteExpiredAndRevoked(
            @Param("expiredBefore") OffsetDateTime expiredBefore,
            @Param("revokedBefore") OffsetDateTime revokedBefore);
}
