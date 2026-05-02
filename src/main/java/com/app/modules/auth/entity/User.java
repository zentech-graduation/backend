package com.app.modules.auth.entity;

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
import org.hibernate.annotations.UpdateTimestamp;

import com.app.modules.auth.converter.UserRoleConverter;
import com.app.modules.auth.converter.UserStatusConverter;
import com.app.modules.auth.enums.UserRole;
import com.app.modules.auth.enums.UserStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Aggregate root for an end user.
 *
 * <p>Maps to the {@code users} table. The denormalized counter columns ({@code follower_count},
 * {@code following_count}, {@code post_count}) are maintained by Postgres triggers (V16) and are
 * intentionally not mapped here to prevent application code from corrupting them.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "username", nullable = false, unique = true, length = 30)
    private String username;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "website_url", columnDefinition = "TEXT")
    private String websiteUrl;

    @Convert(converter = UserRoleConverter.class)
    @Column(name = "role", nullable = false, columnDefinition = "user_role")
    private UserRole role;

    @Convert(converter = UserStatusConverter.class)
    @Column(name = "status", nullable = false, columnDefinition = "user_status")
    private UserStatus status;

    @Column(name = "is_private", nullable = false)
    private boolean isPrivate;

    @Column(name = "is_verified", nullable = false)
    private boolean isVerified;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
