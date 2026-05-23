package com.app.common.messaging.exception;

/** Non-retryable message failure. The message should be routed to DLQ for investigation. */
public class PermanentMessageException extends RuntimeException {

    public PermanentMessageException(String message) {
        super(message);
    }

    public PermanentMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
