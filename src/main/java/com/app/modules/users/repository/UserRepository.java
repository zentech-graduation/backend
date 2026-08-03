package com.app.modules.users.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.app.common.response.UserSummaryResponse;
import com.app.modules.users.entity.User;

@org.springframework.stereotype.Repository
public interface UserRepository extends Repository<User, UUID> {

    User save(User user);

    /**
     * Projects the requested non soft-deleted users onto the shared public summary shape in a
     * single query. Soft-deleted and unknown ids are simply absent from the result; the caller
     * substitutes a placeholder for them.
     *
     * @param ids user ids to project; duplicates are harmless
     * @return summaries for the ids that resolve to a live user, in no guaranteed order
     */
    @Query(
            "SELECT new com.app.common.response.UserSummaryResponse("
                    + "u.id, u.username, u.displayName, u.avatarUrl, u.isVerified) "
                    + "FROM User u WHERE u.id IN :ids AND u.deletedAt IS NULL")
    List<UserSummaryResponse> findSummariesByIdIn(@Param("ids") Collection<UUID> ids);

    /**
     * Case-insensitive substring search over {@code username}, ordered by popularity.
     *
     * <p>Uses {@code ILIKE '%q%'}, which the {@code gin_trgm_ops} index {@code
     * idx_users_username_trgm} (V15) accelerates. The {@code %} similarity operator is deliberately
     * not used: measured against a 200,000-row table it returned every row from the index and
     * discarded 174,846 on recheck for a 200 ms execution, because short similar usernames all fall
     * within the default similarity threshold.
     *
     * <p>Excludes soft-deleted rows, non-active accounts, and the viewer themselves. Blocked users
     * are deliberately <em>not</em> excluded - omitting them would leak the block set to a caller
     * who compares results before and after being blocked. The block relationship travels to the
     * client as viewer state instead.
     *
     * <p>{@code id} is the final sort key so the offset window stays deterministic when two
     * accounts share a {@code follower_count}.
     *
     * @param query substring to match against username; already trimmed and length-checked
     * @param viewerId the requesting user, excluded from their own results
     * @param limit maximum number of rows to return
     * @param offset number of leading rows to skip
     * @return matching live, active users ordered by {@code follower_count} descending, then {@code
     *     username}, then {@code id}
     */
    @Query(
            value =
                    "SELECT id, username, display_name, avatar_url, is_verified FROM users"
                            + " WHERE deleted_at IS NULL"
                            + " AND status = 'active'"
                            + " AND username ILIKE '%' || :query || '%'"
                            + " AND id <> :viewerId"
                            + " ORDER BY follower_count DESC, username ASC, id ASC"
                            + " LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<UserSearchProjection> searchByUsername(
            @Param("query") String query,
            @Param("viewerId") UUID viewerId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    /**
     * Resolves an active user by username, comparing case-insensitively.
     *
     * <p>Username identity is case-insensitive while stored casing is preserved for display, so the
     * comparison must normalize both sides rather than assume the stored value is lowercase. Seeks
     * on {@code idx_users_username_lower} (V43), a table-wide unique functional index, and applies
     * {@code deleted_at IS NULL} as a filter. At most one row can share a lowercased username, so
     * the filter never discards more than one row.
     *
     * @param username username in any casing
     * @return the matching active user, or empty when no active account holds that name
     */
    @Query(
            "SELECT u FROM User u WHERE lower(u.username) = lower(:username) AND u.deletedAt IS NULL")
    Optional<User> findByUsernameAndDeletedAtIsNull(@Param("username") String username);

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Loads only the security-relevant fields for an active (non soft-deleted) user, used by the
     * JWT filter to authenticate requests without hydrating the full {@code User} aggregate.
     */
    Optional<UserSecurityProjection> findProjectedByIdAndDeletedAtIsNull(UUID id);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    /**
     * Reports whether an active account already holds this username, comparing case-insensitively.
     *
     * <p>Served by {@code idx_users_username_lower} (V43).
     *
     * <p>Currently unused. Availability checks deliberately use the table-wide {@link
     * #existsByUsername} instead, because soft delete does not release a username; see {@code
     * GLOBAL_RULES.md} section 3.
     *
     * @param username username in any casing
     * @return true when an active account holds that name under case-insensitive comparison
     */
    @Query(
            "SELECT COUNT(u) > 0 FROM User u "
                    + "WHERE lower(u.username) = lower(:username) AND u.deletedAt IS NULL")
    boolean existsByUsernameAndDeletedAtIsNull(@Param("username") String username);

    // Table-wide checks — used for uniqueness validation consistent with DB UNIQUE constraints
    // that have no partial index excluding soft-deleted rows.
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Reports whether any account, including a soft-deleted one, holds this username under
     * case-insensitive comparison.
     *
     * <p>Deliberately table-wide: soft delete does not release a username and no purge job exists,
     * so a registration check that skipped soft-deleted rows would pass and then fail on the {@code
     * users_username_key} constraint.
     *
     * <p>Served by {@code idx_users_username_lower} (V43), which is table-wide and so answers a
     * query spanning soft-deleted rows. V42's partial index could not, and needed a second
     * non-unique index alongside it; V43 collapsed the two.
     *
     * @param username username in any casing
     * @return true when any account, live or soft-deleted, holds that name
     */
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE lower(u.username) = lower(:username)")
    boolean existsByUsername(@Param("username") String username);
}
