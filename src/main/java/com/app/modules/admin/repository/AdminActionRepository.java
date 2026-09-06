package com.app.modules.admin.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import com.app.modules.admin.entity.AdminAction;

@org.springframework.stereotype.Repository
public interface AdminActionRepository
        extends Repository<AdminAction, UUID>, AdminActionRepositoryCustom {

    Optional<AdminAction> findById(UUID id);
}
