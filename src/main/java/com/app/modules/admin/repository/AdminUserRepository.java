package com.app.modules.admin.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.app.modules.users.entity.User;

/**
 * Administrative access to {@code users}, kept separate from {@code UserRepository} so the
 * soft-delete inversion documented on {@link AdminUserRepositoryCustom} cannot leak into an
 * ordinary read path. Only the admin module injects this interface.
 */
@org.springframework.stereotype.Repository
public interface AdminUserRepository extends Repository<User, UUID>, AdminUserRepositoryCustom {

    /**
     * Returns a lapsed fixed-term suspension to active, if it is still lapsed and still suspended.
     *
     * <p>Takes no lock. Every component of the predicate is checked in the statement itself, so two
     * concurrent callers cannot both apply it: whichever commits first changes {@code status},
     * which invalidates the predicate for the other. A zero row count therefore means the work was
     * already done, not that it failed, and both callers must treat it as success.
     *
     * @param userId account to reinstate
     * @return {@code 1} when this call performed the reinstatement, {@code 0} when it did not apply
     */
    @Modifying
    @Query(
            value =
                    "UPDATE users"
                            + " SET status = 'active', suspended_until = NULL"
                            + " WHERE id = :userId"
                            + " AND status = 'suspended'"
                            + " AND suspended_until IS NOT NULL"
                            + " AND suspended_until <= now()",
            nativeQuery = true)
    int reinstateExpiredSuspension(@Param("userId") UUID userId);

    /**
     * Reads the current status of one account, spanning soft-deleted rows.
     *
     * <p>Native and scalar on purpose: it must not be answered from the persistence context,
     * because the reinstatement above is an SQL update that the context does not see. Reading the
     * row through the entity would return the pre-update status.
     *
     * @param userId account to read
     * @return the {@code user_status} value as its lowercase wire form, or null when no row holds
     *     that id
     */
    @Query(value = "SELECT u.status::text FROM users u WHERE u.id = :userId", nativeQuery = true)
    String findStatusIncludingDeleted(@Param("userId") UUID userId);
}
