package com.app.modules.users.entity;

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

import com.app.modules.users.converter.UserRoleConverter;
import com.app.modules.users.converter.UserStatusConverter;
import com.app.modules.users.enums.UserRole;
import com.app.modules.users.enums.UserStatus;

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
 * intentionally marked {@code insertable = false, updatable = false}.
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

    /** Maintained exclusively by Postgres trigger {@code trg_follow_counts} (V16). */
    @Column(name = "follower_count", insertable = false, updatable = false)
    private int followerCount;

    /** Maintained exclusively by Postgres trigger {@code trg_follow_counts} (V16). */
    @Column(name = "following_count", insertable = false, updatable = false)
    private int followingCount;

    /** Maintained exclusively by Postgres trigger {@code trg_post_count} (V16). */
    @Column(name = "post_count", insertable = false, updatable = false)
    private int postCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
