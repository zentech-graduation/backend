package com.app.modules.story.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import com.app.modules.users.entity.User;

/**
 * Module-local read-only repository over the users module's {@link User} entity.
 *
 * <p>Mirrors the {@code SocialUserRepository} precedent: cross-module data is read through a
 * repository owned by this module instead of injecting another module's repository bean.
 */
@org.springframework.stereotype.Repository
public interface StoryUserRepository extends Repository<User, UUID> {

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    List<User> findAllByIdInAndDeletedAtIsNull(Collection<UUID> ids);
}
