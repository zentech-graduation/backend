package com.app.modules.admin.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.app.modules.admin.entity.UserStrike;

/** Reads and writes for {@code user_strikes}. Injected only inside the admin module. */
@org.springframework.stereotype.Repository
public interface UserStrikeRepository extends Repository<UserStrike, UUID> {

    UserStrike saveAndFlush(UserStrike strike);

    UserStrike save(UserStrike strike);

    Optional<UserStrike> findById(UUID id);

    /**
     * Counts the account's unrevoked strikes.
     *
     * <p>The next strike's number is this plus one. A revoked strike is not counted, so revoking
     * one frees its number, which is what lets an administrator reverse a decision without leaving
     * a hole that {@code uq_user_strikes_active_number} would then refuse to let anything fill.
     *
     * @param userId account being counted
     * @return number of unrevoked strikes
     */
    @Query(
            value =
                    "SELECT count(*) FROM user_strikes s"
                            + " WHERE s.user_id = :userId AND s.revoked_at IS NULL",
            nativeQuery = true)
    long countActiveStrikes(@Param("userId") UUID userId);

    /**
     * First keyset page of an account's unrevoked strikes, newest first.
     *
     * @param userId account whose strikes to list
     * @param limit maximum rows to return
     * @return the page
     */
    @Query(
            value =
                    "SELECT * FROM user_strikes s"
                            + " WHERE s.user_id = :userId AND s.revoked_at IS NULL"
                            + " ORDER BY s.created_at DESC, s.id DESC LIMIT :limit",
            nativeQuery = true)
    List<UserStrike> findFirstActivePage(@Param("userId") UUID userId, @Param("limit") int limit);

    /**
     * Keyset page of an account's unrevoked strikes after a cursor position, newest first.
     *
     * @param userId account whose strikes to list
     * @param cursorCreatedAt creation time of the last row on the previous page
     * @param cursorId identifier of the last row on the previous page
     * @param limit maximum rows to return
     * @return the page
     */
    @Query(
            value =
                    "SELECT * FROM user_strikes s"
                            + " WHERE s.user_id = :userId AND s.revoked_at IS NULL"
                            + " AND (s.created_at, s.id) < (:cursorCreatedAt, :cursorId)"
                            + " ORDER BY s.created_at DESC, s.id DESC LIMIT :limit",
            nativeQuery = true)
    List<UserStrike> findActivePageAfterCursor(
            @Param("userId") UUID userId,
            @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            @Param("limit") int limit);
}
