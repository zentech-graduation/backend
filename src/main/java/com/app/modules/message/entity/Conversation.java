package com.app.modules.message.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A 1-1 or group messaging thread.
 *
 * <p>Maps to the {@code conversations} table. {@code last_message_at} and {@code updated_at} are
 * maintained exclusively by Postgres triggers {@code trg_conversation_last_message} (AFTER INSERT
 * on {@code messages}) and {@code trg_conversations_updated_at} (V16); neither is ever written from
 * application code. There is no soft-delete column - a conversation is hidden per-user via {@code
 * conversation_participants.left_at}, not by deleting the row.
 */
@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "is_group", nullable = false, updatable = false)
    private boolean isGroup;

    @Column(name = "group_name", length = 100)
    private String groupName;

    @Column(name = "group_avatar_url", columnDefinition = "TEXT")
    private String groupAvatarUrl;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    /** Maintained exclusively by Postgres trigger {@code trg_conversation_last_message} (V16). */
    @Column(name = "last_message_at", insertable = false, updatable = false)
    private OffsetDateTime lastMessageAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Maintained exclusively by Postgres triggers (V16); never written from application code. */
    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
