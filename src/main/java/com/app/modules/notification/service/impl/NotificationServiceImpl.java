package com.app.modules.notification.service.impl;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.response.CursorPageResponse;
import com.app.modules.notification.dto.response.NotificationResponse;
import com.app.modules.notification.entity.Notification;
import com.app.modules.notification.entity.enums.NotificationType;
import com.app.modules.notification.mapper.NotificationMapper;
import com.app.modules.notification.repository.NotificationRepository;
import com.app.modules.notification.service.NotificationService;
import com.app.modules.social.repository.BlockRepository;
import com.app.modules.users.entity.UserSettings;
import com.app.modules.users.repository.UserSettingsRepository;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final BlockRepository blockRepository;
    private final NotificationMapper notificationMapper;

    public NotificationServiceImpl(
            NotificationRepository notificationRepository,
            UserSettingsRepository userSettingsRepository,
            BlockRepository blockRepository,
            NotificationMapper notificationMapper) {
        this.notificationRepository = notificationRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.blockRepository = blockRepository;
        this.notificationMapper = notificationMapper;
    }

    @Override
    @Transactional
    public void create(
            UUID actorId,
            UUID recipientId,
            NotificationType type,
            String entityType,
            UUID entityId) {
        if (actorId != null && actorId.equals(recipientId)) {
            return;
        }

        UserSettings settings = userSettingsRepository.findById(recipientId).orElse(null);
        if (settings != null && !isTypeEnabled(type, settings)) {
            return;
        }

        if (actorId != null && blockRepository.existsBetween(actorId, recipientId)) {
            return;
        }

        Notification notification =
                Notification.builder()
                        .recipientId(recipientId)
                        .actorId(actorId)
                        .type(type)
                        .entityType(entityType)
                        .entityId(entityId)
                        .build();
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void markAsRead(UUID notificationId, UUID recipientId) {
        Notification notification =
                notificationRepository
                        .findByIdAndRecipientId(notificationId, recipientId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.FORBIDDEN));
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(OffsetDateTime.now(ZoneOffset.UTC));
            notificationRepository.save(notification);
        }
    }

    @Override
    @Transactional
    public void markAllAsRead(UUID recipientId) {
        notificationRepository.markAllAsRead(recipientId, OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(UUID recipientId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(recipientId);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<NotificationResponse> listNotifications(
            UUID recipientId, UUID cursor, int limit) {
        UUID resolvedCursor = null;
        OffsetDateTime cursorTime = null;
        if (cursor != null) {
            Optional<Notification> pivot = notificationRepository.findById(cursor);
            if (pivot.isPresent()) {
                resolvedCursor = cursor;
                cursorTime = pivot.get().getCreatedAt();
            }
            // Pivot absent means the cursor notification was deleted; fall back to the first page.
        }

        List<Notification> rows =
                notificationRepository.findByRecipientIdWithCursor(
                        recipientId, resolvedCursor, cursorTime, PageRequest.of(0, limit));
        List<NotificationResponse> content = notificationMapper.toResponseList(rows);
        String startCursor = rows.isEmpty() ? null : rows.get(0).getId().toString();
        String endCursor = rows.isEmpty() ? null : rows.get(rows.size() - 1).getId().toString();
        return CursorPageResponse.of(content, limit, startCursor, endCursor, cursor != null);
    }

    private boolean isTypeEnabled(NotificationType type, UserSettings settings) {
        return switch (type) {
            case FOLLOW, FOLLOW_REQUEST -> settings.isNotifyFollows();
            case LIKE_POST, LIKE_COMMENT -> settings.isNotifyLikes();
            case COMMENT_POST, REPLY_COMMENT -> settings.isNotifyComments();
            case MENTION_POST, MENTION_COMMENT -> settings.isNotifyMentions();
            case MESSAGE -> settings.isNotifyMessages();
            // STORY_VIEW has no user_settings toggle; it is never preference-suppressed.
            case STORY_VIEW -> true;
        };
    }
}
