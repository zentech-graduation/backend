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
 * One strike against an account, mapped to {@code user_strikes}.
 *
 * <p>A strike is the consequence of three active warnings. Its number decides the penalty: one
 * suspends for seven days, two for thirty, three and above ban permanently. The number is not
 * capped, so an account an administrator unbanned by hand can still take a fourth.
 */
@Entity
@Table(name = "user_strikes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStrike {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /**
     * Position in the account's active strike sequence, starting at 1.
     *
     * <p>Unique per account among unrevoked strikes, enforced by {@code
     * uq_user_strikes_active_number}. That index, not the count this value is derived from, is what
     * stops two concurrent warnings issuing the same strike twice.
     */
    @Column(name = "strike_number", nullable = false, updatable = false)
    private short strikeNumber;

    /** Moderator whose warning tipped the account over, or null if that account was deleted. */
    @Column(name = "triggered_by", updatable = false)
    private UUID triggeredBy;

    /** Audit row written in the same transaction as this strike. Never null. */
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
