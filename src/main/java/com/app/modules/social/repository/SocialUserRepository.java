package com.app.modules.social.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import com.app.modules.users.entity.User;

@org.springframework.stereotype.Repository
public interface SocialUserRepository extends Repository<User, UUID> {

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByIdAndDeletedAtIsNull(UUID id);

    List<User> findAllByIdInAndDeletedAtIsNull(Collection<UUID> ids);
}
