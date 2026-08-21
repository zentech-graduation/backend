package com.app.modules.admin.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.app.modules.admin.entity.UserWarning;

/** Reads and writes for {@code user_warnings}. Injected only inside the admin module. */
@org.springframework.stereotype.Repository
public interface UserWarningRepository extends Repository<UserWarning, UUID> {

    UserWarning saveAndFlush(UserWarning warning);

    UserWarning save(UserWarning warning);

    Optional<UserWarning> findById(UUID id);

    /**
     * Counts the warnings that currently count toward the next strike.
     *
     * <p>Three conditions compose, and all three matter. A revoked warning never counts. A warning
     * issued before the account's most recent unrevoked strike never counts, because a strike
     * consumes the warnings that produced it and the count restarts after one. A warning older than
     * the retention window never counts, so an account that behaves for long enough is not
     * disciplined for something it did years ago.
     *
     * <p>Composed in one statement rather than as three filters in Java because getting the
     * composition wrong changes how fast accounts are banned without failing anything.
     *
     * @param userId account being counted
     * @param windowStart oldest creation time that still counts
     * @return number of warnings that count toward the next strike
     */
    @Query(
            value =
                    "SELECT count(*) FROM user_warnings w"
                            + " WHERE w.user_id = :userId"
                            + " AND w.revoked_at IS NULL"
                            + " AND w.created_at > COALESCE((SELECT max(s.created_at) FROM user_strikes"
                            + " s WHERE s.user_id = :userId AND s.revoked_at IS NULL), :epoch)"
                            + " AND w.created_at > :windowStart",
            nativeQuery = true)
    long countActiveWarnings(
            @Param("userId") UUID userId,
            @Param("windowStart") OffsetDateTime windowStart,
            @Param("epoch") OffsetDateTime epoch);

    /**
     * Identifiers of the warnings counted by {@link #countActiveWarnings}, newest first.
     *
     * <p>Recorded in the strike's audit metadata so the decision can be reconstructed later, once
     * the warnings it rested on no longer count.
     *
     * @param userId account being counted
     * @param windowStart oldest creation time that still counts
     * @param epoch value standing in for "no strike yet"
     * @return the counting warnings' identifiers
     */
    @Query(
            value =
                    "SELECT w.id FROM user_warnings w"
                            + " WHERE w.user_id = :userId"
                            + " AND w.revoked_at IS NULL"
                            + " AND w.created_at > COALESCE((SELECT max(s.created_at) FROM user_strikes"
                            + " s WHERE s.user_id = :userId AND s.revoked_at IS NULL), :epoch)"
                            + " AND w.created_at > :windowStart"
                            + " ORDER BY w.created_at DESC, w.id DESC",
            nativeQuery = true)
    List<UUID> findActiveWarningIds(
            @Param("userId") UUID userId,
            @Param("windowStart") OffsetDateTime windowStart,
            @Param("epoch") OffsetDateTime epoch);

    /**
     * First keyset page of an account's unrevoked warnings, newest first.
     *
     * @param userId account whose warnings to list
     * @param limit maximum rows to return
     * @return the page
     */
    @Query(
            value =
                    "SELECT * FROM user_warnings w"
                            + " WHERE w.user_id = :userId AND w.revoked_at IS NULL"
                            + " ORDER BY w.created_at DESC, w.id DESC LIMIT :limit",
            nativeQuery = true)
    List<UserWarning> findFirstActivePage(@Param("userId") UUID userId, @Param("limit") int limit);

    /**
     * Keyset page of an account's unrevoked warnings after a cursor position, newest first.
     *
     * @param userId account whose warnings to list
     * @param cursorCreatedAt creation time of the last row on the previous page
     * @param cursorId identifier of the last row on the previous page
     * @param limit maximum rows to return
     * @return the page
     */
    @Query(
            value =
                    "SELECT * FROM user_warnings w"
                            + " WHERE w.user_id = :userId AND w.revoked_at IS NULL"
                            + " AND (w.created_at, w.id) < (:cursorCreatedAt, :cursorId)"
                            + " ORDER BY w.created_at DESC, w.id DESC LIMIT :limit",
            nativeQuery = true)
    List<UserWarning> findActivePageAfterCursor(
            @Param("userId") UUID userId,
            @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            @Param("limit") int limit);
}
