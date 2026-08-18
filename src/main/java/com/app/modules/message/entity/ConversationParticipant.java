package com.app.modules.message.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Membership row of one user in one conversation.
 *
 * <p>Maps to {@code conversation_participants}. {@code left_at IS NOT NULL} means the user is no
 * longer active in the conversation (left, or was removed by a group admin); it is never hard
 * deleted so history stays consistent for the remaining participants. {@code last_read_at} drives
 * the unread-count computation and is updated only by the owning user marking the conversation
 * read.
 */
@Entity
@Table(name = "conversation_participants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationParticipant {

    @EmbeddedId private ConversationParticipantId id;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private OffsetDateTime joinedAt;

    /** Set when the user leaves or is removed; {@code null} while actively participating. */
    @Column(name = "left_at")
    private OffsetDateTime leftAt;

    /** Set by the owning user marking the conversation read; drives the unread-count query. */
    @Column(name = "last_read_at")
    private OffsetDateTime lastReadAt;
}
