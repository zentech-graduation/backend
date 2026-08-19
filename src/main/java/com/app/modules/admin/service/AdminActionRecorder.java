package com.app.modules.admin.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.app.modules.admin.dto.response.AdminActionResponse;
import com.app.modules.admin.entity.AdminAction;
import com.app.modules.admin.enums.AdminActionType;
import com.app.modules.admin.mapper.AdminActionMapper;
import com.app.modules.admin.repository.AdminActionRepository;

/**
 * Single writer for the {@code admin_actions} audit log.
 *
 * <p>Deliberately carries no transaction annotation so every call joins the caller's transaction.
 * An audit row that could commit independently of the state change it describes would let the log
 * and the data disagree, which defeats the point of an audit log.
 *
 * <p>{@code metadata} is server-side fact only. No caller may forward a client-supplied map here:
 * an audit log a client can write into records what the client claims happened, not what happened.
 */
@Component
public class AdminActionRecorder {

    private final AdminActionRepository adminActionRepository;
    private final AdminActionMapper adminActionMapper;

    public AdminActionRecorder(
            AdminActionRepository adminActionRepository, AdminActionMapper adminActionMapper) {
        this.adminActionRepository = adminActionRepository;
        this.adminActionMapper = adminActionMapper;
    }

    /**
     * Appends one immutable audit row and returns it as a response.
     *
     * @param actorId the acting account, or null for an action taken by a scheduled job
     * @param actionType the moderation action performed
     * @param targetUserId the affected account, or null when the action has no user target
     * @param targetEntityType polymorphic target discriminator, for example {@code "user"}
     * @param targetEntityId polymorphic target identifier
     * @param reportId the report that prompted the action, or null
     * @param reason human-readable justification; trimmed before persisting
     * @param metadata server-derived structured context, or null
     * @return the persisted audit row, with {@code createdAt} already populated
     */
    public AdminActionResponse record(
            UUID actorId,
            AdminActionType actionType,
            UUID targetUserId,
            String targetEntityType,
            UUID targetEntityId,
            UUID reportId,
            String reason,
            Map<String, Object> metadata) {
        AdminAction action =
                AdminAction.builder()
                        .adminId(actorId)
                        .actionType(actionType)
                        .targetUserId(targetUserId)
                        .targetEntityType(targetEntityType)
                        .targetEntityId(targetEntityId)
                        .reportId(reportId)
                        .reason(reason == null ? null : reason.trim())
                        .metadata(metadata)
                        .build();
        return adminActionMapper.toResponse(adminActionRepository.insert(action));
    }
}
