package com.app.modules.admin.enums;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Moderation action types persisted in the PostgreSQL {@code admin_action_type} enum. */
public enum AdminActionType {
    BAN_USER,
    UNBAN_USER,
    SUSPEND_USER,
    UNSUSPEND_USER,
    REMOVE_POST,
    RESTORE_POST,
    REMOVE_COMMENT,
    RESTORE_COMMENT,
    RESOLVE_REPORT,
    DISMISS_REPORT;

    @JsonCreator
    public static AdminActionType fromJson(String value) {
        return value == null || value.isBlank()
                ? null
                : AdminActionType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
