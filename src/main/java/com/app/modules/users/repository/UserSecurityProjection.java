package com.app.modules.users.repository;

import java.util.UUID;

import com.app.modules.users.enums.UserRole;
import com.app.modules.users.enums.UserStatus;

/**
 * Slim read projection exposing only the fields the security filter chain needs to build an
 * authenticated principal and enforce account state. Avoids hydrating the full {@code User}
 * aggregate (counters, profile text) on every authenticated request.
 */
public interface UserSecurityProjection {

    UUID getId();

    String getEmail();

    UserRole getRole();

    UserStatus getStatus();
}
