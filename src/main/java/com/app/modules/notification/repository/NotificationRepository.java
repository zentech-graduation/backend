package com.app.modules.notification.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.modules.notification.entity.Notification;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * Fetches a cursor-paginated list of notifications for the recipient, ordered by creation time
     * descending. Pass {@code cursor = null} to start from the first page.
     */
    @Query(
            "SELECT n FROM Notification n WHERE n.recipientId = :recipientId "
                    + "AND (:cursor IS NULL OR n.createdAt < :cursorTime "
                    + "  OR (n.createdAt = :cursorTime AND n.id < :cursor)) "
                    + "ORDER BY n.createdAt DESC, n.id DESC")
    List<Notification> findByRecipientIdWithCursor(
            @Param("recipientId") UUID recipientId,
            @Param("cursor") UUID cursor,
            @Param("cursorTime") OffsetDateTime cursorTime,
            Pageable pageable);

    /**
     * Returns the count of unread notifications for the given recipient. Backed by the partial
     * index {@code idx_notifications_unread}.
     */
    long countByRecipientIdAndIsReadFalse(UUID recipientId);

    /**
     * Looks up a single notification by its id scoped to a specific recipient. Used for ownership
     * validation before marking as read.
     */
    Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);

    /** Marks all unread notifications for the recipient as read in a single bulk update. */
    @Modifying
    @Query(
            "UPDATE Notification n SET n.isRead = true, n.readAt = :now "
                    + "WHERE n.recipientId = :recipientId AND n.isRead = false")
    int markAllAsRead(@Param("recipientId") UUID recipientId, @Param("now") OffsetDateTime now);
}
