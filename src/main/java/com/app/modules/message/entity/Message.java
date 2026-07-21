package com.app.modules.message.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import com.app.modules.message.converter.MessageTypeConverter;
import com.app.modules.message.enums.MessageType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single message within a conversation: text, media, a shared post/story, or a reply.
 *
 * <p>Maps to the {@code messages} table. Uses the module's hybrid soft-delete: {@code is_deleted}
 * and {@code deleted_at} are set together on delete, and {@code content} is cleared to a tombstone.
 * Unlike the {@code deleted_at}-only pattern used elsewhere, rows are never filtered out by an
 * entity-level restriction - deleted messages stay visible in history so clients can render a
 * "message deleted" placeholder in place. {@code sender_id} is nullable: deleting a user's account
 * sets it to {@code null} (V32) rather than destroying the message, preserving history for the
 * remaining participants.
 */
@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    /** Nullable: set to {@code null} when the sending user's account is deleted (V32). */
    @Column(name = "sender_id", updatable = false)
    private UUID senderId;

    @Convert(converter = MessageTypeConverter.class)
    @Column(name = "message_type", nullable = false, columnDefinition = "message_type")
    private MessageType messageType;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "media_asset_id", updatable = false)
    private UUID mediaAssetId;

    @Column(name = "shared_post_id", updatable = false)
    private UUID sharedPostId;

    @Column(name = "shared_story_id", updatable = false)
    private UUID sharedStoryId;

    @Column(name = "reply_to_id", updatable = false)
    private UUID replyToId;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
