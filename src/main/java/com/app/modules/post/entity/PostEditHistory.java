package com.app.modules.post.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Append-only audit row recording a post caption edit.
 *
 * <p>Maps to {@code post_edit_history} (V22). Rows are inserted on every caption update and are
 * never updated or deleted by application code; there is no soft-delete column. Removal happens
 * only through the post/user hard-delete FK cascade.
 */
@Entity
@Table(name = "post_edit_history")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostEditHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private UUID postId;

    @Column(name = "editor_id", nullable = false, updatable = false)
    private UUID editorId;

    /** Caption value before the edit; {@code null} when the post had no caption. */
    @Column(name = "previous_caption", columnDefinition = "TEXT", updatable = false)
    private String previousCaption;

    @Column(name = "edited_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime editedAt;
}
