package com.app.modules.comment.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import com.app.modules.users.entity.User;

/**
 * Module-local read-only repository over the users module's {@link User} entity.
 *
 * <p>Mirrors the {@code PostUserRepository} precedent: cross-module data is read through a
 * repository owned by this module instead of injecting another module's repository bean. Used to
 * resolve {@code @mention} usernames to user ids.
 */
@org.springframework.stereotype.Repository
public interface CommentUserRepository extends Repository<User, UUID> {

    Optional<User> findByUsernameAndDeletedAtIsNull(String username);
}
