package com.app.modules.auth.repository;

import java.time.OffsetDateTime;
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

    /**
     * Revokes every active refresh token belonging to the given user.
     *
     * @param userId user whose tokens should be revoked
     * @param now revocation timestamp to record
     */
    @Modifying
    @Query(
            "UPDATE RefreshToken r SET r.revokedAt = :now "
                    + "WHERE r.userId = :userId AND r.revokedAt IS NULL")
    void revokeAllActiveByUserId(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);

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
}
