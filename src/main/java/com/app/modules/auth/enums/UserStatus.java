package com.app.modules.auth.enums;

/** Lifecycle status enumeration mapped to the Postgres {@code user_status} enum. */
public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    DEACTIVATED,
    BANNED
}
