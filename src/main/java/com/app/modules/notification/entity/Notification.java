package com.app.modules.notification.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.app.modules.notification.entity.converter.NotificationTypeConverter;
import com.app.modules.notification.entity.enums.NotificationType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** In-app notification delivered to a recipient when a tracked social event occurs. */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private UUID recipientId;

    @Column(name = "actor_id", nullable = true)
    private UUID actorId;

    @Convert(converter = NotificationTypeConverter.class)
    @Column(name = "type", nullable = false, updatable = false)
    private NotificationType type;

    @Column(name = "entity_type", length = 50, nullable = true)
    private String entityType;

    @Column(name = "entity_id", nullable = true)
    private UUID entityId;

    @Column(name = "post_id", nullable = true, updatable = false)
    private UUID postId;

    @Column(name = "message", nullable = true, columnDefinition = "TEXT")
    private String message;

    @Setter
    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Setter
    @Column(name = "read_at", nullable = true)
    private OffsetDateTime readAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
