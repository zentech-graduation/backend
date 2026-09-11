package com.app.modules.users.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.modules.users.entity.UserSettings;

@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {

    /**
     * Resolves the account behind an unsubscribe link.
     *
     * <p>The only access path that query has: the recipient following the link from a mail client
     * has no session, so there is nothing else to narrow it by. Served by {@code
     * uq_user_settings_unsubscribe_token}.
     *
     * @param tokenHash SHA-256 hex of the raw token from the link
     * @return the settings row holding that token, or empty
     */
    Optional<UserSettings> findByUnsubscribeToken(String tokenHash);

    /** Accounts among the given set that have opted out of campaign mail. */
    @Query(
            "SELECT s.userId FROM UserSettings s WHERE s.userId IN :userIds AND s.emailOptOut = true")
    List<UUID> findOptedOutUserIds(@Param("userIds") Collection<UUID> userIds);
}
