package com.app.modules.admin.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.app.modules.admin.entity.AdminAction;
import com.app.modules.admin.enums.AdminActionType;

public interface AdminActionRepositoryCustom {

    AdminAction insert(AdminAction action);

    List<AdminAction> findActions(
            UUID adminId,
            UUID targetUserId,
            AdminActionType actionType,
            OffsetDateTime cursorCreatedAt,
            UUID cursorId,
            int limit);
}
