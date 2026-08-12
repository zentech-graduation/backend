package com.app.modules.message.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

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

    /**
     * Canonical {@code minUserId:maxUserId} key for a 1-1 conversation, null for group
     * conversations. Computed by the database (never by application code) so it always agrees with
     * the {@code LEAST}/{@code GREATEST} ordering the uniqueness backstop relies on.
     */
    @Column(name = "direct_pair_key", updatable = false)
    private String directPairKey;

    /** Maintained exclusively by Postgres trigger {@code trg_conversation_last_message} (V16). */
    @Column(name = "last_message_at", insertable = false, updatable = false)
    private OffsetDateTime lastMessageAt;

    // Database-generated like updated_at, rather than @CreationTimestamp. The column's DEFAULT
    // NOW() and the sibling updated_at default both resolve to the same transaction timestamp, so
    // the two agree exactly at insert. Under @CreationTimestamp this value came from the JVM
    // clock while updated_at came from Postgres, leaving them permanently unequal on a
    // conversation nobody had touched. Hibernate already re-reads updated_at after every insert,
    // so reading this one back costs no extra round trip.
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    // trg_conversations_updated_at (V16) is the sole writer of this column; Hibernate never sends
    // it in an INSERT or UPDATE and instead re-selects it afterward so the entity reflects the
    // trigger-written value. Without @Generated it was never re-read at all, so the entity held
    // null after an insert while the NOT NULL column always carried a value.
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
