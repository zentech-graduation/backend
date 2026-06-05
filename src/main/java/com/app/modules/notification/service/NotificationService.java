package com.app.modules.notification.service;

import java.util.UUID;

import com.app.common.response.CursorPageResponse;
import com.app.modules.notification.dto.response.NotificationResponse;
import com.app.modules.notification.entity.enums.NotificationType;

public interface NotificationService {

    /**
     * Creates a notification for the recipient if all creation guards pass: actor is not the
     * recipient, the recipient has the governing user-settings toggle enabled for the type, and
     * the actor is not blocked by the recipient.
     *
     * <p>Toggle mapping: {@code FOLLOW}/{@code FOLLOW_REQUEST} check {@code notify_follows},
     * {@code LIKE_POST}/{@code LIKE_COMMENT} check {@code notify_likes}, {@code COMMENT_POST}/
     * {@code REPLY_COMMENT} check {@code notify_comments}, {@code MENTION_POST}/{@code
     * MENTION_COMMENT} check {@code notify_mentions}, and {@code MESSAGE} checks {@code
     * notify_messages}. {@code STORY_VIEW} has no toggle and is never preference-suppressed.
     *
     * @param actorId user who triggered the action; may be null for system notifications
     * @param recipientId user who should receive the notification
     * @param type notification type
     * @param entityType polymorphic entity type; null for follow/follow_request
     * @param entityId polymorphic entity id; null for follow/follow_request
     */
    void create(
            UUID actorId,
            UUID recipientId,
            NotificationType type,
            String entityType,
            UUID entityId);

    /**
     * Marks the notification as read. Throws {@link com.app.common.exception.AppException} with
     * {@link com.app.common.enums.ApiErrorCode#FORBIDDEN} if the notification does not exist or
     * belongs to a different recipient.
     *
     * @param notificationId notification to mark as read
     * @param recipientId authenticated user's id
     */
    void markAsRead(UUID notificationId, UUID recipientId);

    /**
     * Marks all unread notifications for the recipient as read in a single operation.
     *
     * @param recipientId authenticated user's id
     */
    void markAllAsRead(UUID recipientId);

    /**
     * Returns the count of unread notifications for the given recipient.
     *
     * @param recipientId authenticated user's id
     * @return count of notifications where is_read = false
     */
    long getUnreadCount(UUID recipientId);

    /**
     * Returns a cursor-paginated list of notifications for the recipient, ordered by creation time
     * descending.
     *
     * @param recipientId authenticated user's id
     * @param cursor id of the last notification on the previous page; null for first page
     * @param limit maximum number of items to return (max 50)
     * @return cursor page response containing notification items
     */
    CursorPageResponse<NotificationResponse> listNotifications(
            UUID recipientId, UUID cursor, int limit);
}
