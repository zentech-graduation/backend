package com.app.modules.users.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.common.response.UserSummaryResponse;
import com.app.modules.users.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

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

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByUsernameAndDeletedAtIsNull(String username);

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Loads only the security-relevant fields for an active (non soft-deleted) user, used by the
     * JWT filter to authenticate requests without hydrating the full {@code User} aggregate.
     */
    Optional<UserSecurityProjection> findProjectedByIdAndDeletedAtIsNull(UUID id);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    boolean existsByUsernameAndDeletedAtIsNull(String username);

    // Table-wide checks — used for uniqueness validation consistent with DB UNIQUE constraints
    // that have no partial index excluding soft-deleted rows.
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);
}
