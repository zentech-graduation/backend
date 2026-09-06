package com.app.common.inbox.enums;

/** Outcome of a consumer idempotency guard execution. */
public enum ProcessedMessageResult {
    PROCESSED,
    DUPLICATE
}
