package com.app.modules.message.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import com.app.modules.users.entity.User;
import com.app.modules.users.enums.UserStatus;

/**
 * Module-local read-only repository over the users module's {@link User} entity.
 *
 * <p>Mirrors the {@code StoryUserRepository} precedent: cross-module data is read through a
 * repository owned by this module instead of injecting another module's repository bean.
 */
@org.springframework.stereotype.Repository
public interface MessageUserRepository extends Repository<User, UUID> {

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    List<User> findAllByIdInAndDeletedAtIsNull(Collection<UUID> ids);

    /** Used to gate WebSocket connections: only a live, active account may hold one open. */
    Optional<User> findByIdAndDeletedAtIsNullAndStatus(UUID id, UserStatus status);
}
