package com.app.modules.admin.entity;

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
 * One warning issued against an account, mapped to {@code user_warnings}.
 *
 * <p>Append-only apart from revocation. Three warnings that are active by the composite predicate
 * in {@code UserDisciplineServiceImpl} produce a strike, which is why revocation is a column here
 * rather than a delete: a deleted warning would silently change how fast the account reaches one.
 */
@Entity
@Table(name = "user_warnings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserWarning {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "issued_by", updatable = false)
    private UUID issuedBy;

    /** Key of a {@code report_reason_configs} row; the reason the moderator cited. */
    @Column(name = "reason_key", nullable = false, length = 50, updatable = false)
    private String reasonKey;

    @Column(name = "note", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String note;

    /** Audit row written in the same transaction as this warning. Never null. */
    @Column(name = "admin_action_id", nullable = false, updatable = false)
    private UUID adminActionId;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
