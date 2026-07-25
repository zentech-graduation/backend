package com.app.common.enums;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/** Machine-readable error codes for all API failure responses. */
@Getter
public enum ApiErrorCode {
    // spotless:off

    // Common
    VALIDATION_ERROR("VALIDATION_ERROR", "Request validation failed", HttpStatus.BAD_REQUEST),
    BAD_REQUEST("BAD_REQUEST", "Invalid request", HttpStatus.BAD_REQUEST),
    INVALID_CURSOR("INVALID_CURSOR", "Malformed pagination cursor", HttpStatus.BAD_REQUEST),
    NOT_FOUND("NOT_FOUND", "Requested resource was not found", HttpStatus.NOT_FOUND),
    FORBIDDEN("FORBIDDEN", "Access to this resource is forbidden", HttpStatus.FORBIDDEN),
    UNAUTHORIZED("UNAUTHORIZED", "Authentication is required", HttpStatus.UNAUTHORIZED),
    INTERNAL_ERROR("INTERNAL_ERROR", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR),
    TOO_MANY_REQUESTS("TOO_MANY_REQUESTS", "The system is busy. Please try again in a few minutes.", HttpStatus.TOO_MANY_REQUESTS),
    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", "External service temporarily unavailable. Please try again later.", HttpStatus.SERVICE_UNAVAILABLE),

    // Auth
    AUTH_INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", "Invalid email or password", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_EXPIRED("AUTH_TOKEN_EXPIRED", "Access token has expired", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_INVALID("AUTH_TOKEN_INVALID", "Invalid access token", HttpStatus.UNAUTHORIZED),
    AUTH_REFRESH_TOKEN_EXPIRED("AUTH_REFRESH_TOKEN_EXPIRED", "Refresh token has expired", HttpStatus.UNAUTHORIZED),
    AUTH_REFRESH_TOKEN_INVALID("AUTH_REFRESH_TOKEN_INVALID", "Invalid or revoked refresh token", HttpStatus.UNAUTHORIZED),
    AUTH_ACCOUNT_LOCKED("AUTH_ACCOUNT_LOCKED", "Account is temporarily locked due to too many failed attempts", HttpStatus.FORBIDDEN),
    AUTH_ACCOUNT_INACTIVE("AUTH_ACCOUNT_INACTIVE", "Account is inactive", HttpStatus.FORBIDDEN),
    AUTH_EMAIL_NOT_VERIFIED("AUTH_EMAIL_NOT_VERIFIED", "Email address has not been verified", HttpStatus.FORBIDDEN),
    AUTH_PASSWORD_MISMATCH("AUTH_PASSWORD_MISMATCH", "Current password is incorrect", HttpStatus.BAD_REQUEST),
    AUTH_RESET_TOKEN_INVALID("AUTH_RESET_TOKEN_INVALID", "Invalid or expired reset token", HttpStatus.BAD_REQUEST),
    AUTH_RESET_TOKEN_EXPIRED("AUTH_RESET_TOKEN_EXPIRED", "Reset token has expired", HttpStatus.GONE),
    AUTH_RESET_TOKEN_USED("AUTH_RESET_TOKEN_USED", "Reset token has already been used", HttpStatus.CONFLICT),
    AUTH_VERIFY_TOKEN_INVALID("AUTH_VERIFY_TOKEN_INVALID", "Email verification token is invalid or has expired", HttpStatus.BAD_REQUEST),
    AUTH_OAUTH2_EXCHANGE_CODE_INVALID("AUTH_OAUTH2_EXCHANGE_CODE_INVALID", "OAuth2 exchange code is invalid or has expired", HttpStatus.BAD_REQUEST),

    // Users
    USER_ALREADY_EXISTS("USER_ALREADY_EXISTS", "An account with these details already exists", HttpStatus.CONFLICT),
    USER_EMAIL_ALREADY_EXISTS("USER_EMAIL_ALREADY_EXISTS", "An account with this email already exists", HttpStatus.CONFLICT),
    USER_USERNAME_ALREADY_EXISTS("USER_USERNAME_ALREADY_EXISTS", "Username is already taken", HttpStatus.CONFLICT),
    USER_NOT_FOUND("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND),

    // Social
    SOCIAL_SELF_FOLLOW("SOCIAL_SELF_FOLLOW", "You cannot follow yourself", HttpStatus.BAD_REQUEST),
    SOCIAL_ALREADY_FOLLOWING("SOCIAL_ALREADY_FOLLOWING", "You are already following this user", HttpStatus.CONFLICT),
    SOCIAL_ALREADY_REQUESTED("SOCIAL_ALREADY_REQUESTED", "Follow request already sent", HttpStatus.CONFLICT),
    SOCIAL_REQUEST_NOT_FOUND("SOCIAL_REQUEST_NOT_FOUND", "Follow request not found", HttpStatus.NOT_FOUND),
    SOCIAL_SELF_BLOCK("SOCIAL_SELF_BLOCK", "You cannot block yourself", HttpStatus.BAD_REQUEST),
    SOCIAL_ALREADY_BLOCKED("SOCIAL_ALREADY_BLOCKED", "User already blocked", HttpStatus.CONFLICT),
    SOCIAL_BLOCKED("SOCIAL_BLOCKED", "This action is not allowed because of a block relationship", HttpStatus.FORBIDDEN),
    SOCIAL_SELF_FOLLOW_NOT_ALLOWED("SOCIAL_SELF_FOLLOW_NOT_ALLOWED", "You cannot follow yourself", HttpStatus.BAD_REQUEST),
    SOCIAL_FOLLOW_ALREADY_EXISTS("SOCIAL_FOLLOW_ALREADY_EXISTS", "Follow relationship already exists", HttpStatus.CONFLICT),
    SOCIAL_FOLLOW_BLOCKED("SOCIAL_FOLLOW_BLOCKED", "Follow is not allowed because a block relationship exists", HttpStatus.FORBIDDEN),

    // Media
    MEDIA_INVALID_METADATA("MEDIA_INVALID_METADATA", "Media metadata is invalid", HttpStatus.BAD_REQUEST),
    MEDIA_STORAGE_KEY_ALREADY_EXISTS("MEDIA_STORAGE_KEY_ALREADY_EXISTS", "Media storage key already exists", HttpStatus.CONFLICT),
    MEDIA_CDN_NOT_CONFIGURED("MEDIA_CDN_NOT_CONFIGURED", "Media CDN is not configured", HttpStatus.SERVICE_UNAVAILABLE),
    MEDIA_STORAGE_NOT_CONFIGURED("MEDIA_STORAGE_NOT_CONFIGURED", "Media object storage is not configured", HttpStatus.SERVICE_UNAVAILABLE),
    MEDIA_UPLOAD_URL_FAILED("MEDIA_UPLOAD_URL_FAILED", "Media upload URL could not be generated", HttpStatus.SERVICE_UNAVAILABLE),

    // Hashtag
    HASHTAG_NOT_FOUND("HASHTAG_NOT_FOUND", "Hashtag not found", HttpStatus.NOT_FOUND),

    // Post
    POST_NOT_FOUND("POST_NOT_FOUND", "Post not found", HttpStatus.NOT_FOUND),
    POST_FORBIDDEN("POST_FORBIDDEN", "You do not have access to this post", HttpStatus.FORBIDDEN),
    POST_ALREADY_LIKED("POST_ALREADY_LIKED", "Post already liked", HttpStatus.CONFLICT),
    POST_ALREADY_SAVED("POST_ALREADY_SAVED", "Post already saved", HttpStatus.CONFLICT),

    // Comment
    COMMENT_NOT_FOUND("COMMENT_NOT_FOUND", "Comment not found", HttpStatus.NOT_FOUND),
    COMMENT_FORBIDDEN("COMMENT_FORBIDDEN", "You do not have permission to perform this action on the comment", HttpStatus.FORBIDDEN),
    COMMENT_MODERATION_REJECTED("COMMENT_MODERATION_REJECTED", "Comment content was rejected by moderation", HttpStatus.UNPROCESSABLE_ENTITY),
    COMMENT_DEPTH_EXCEEDED("COMMENT_DEPTH_EXCEEDED", "Maximum comment nesting depth exceeded", HttpStatus.BAD_REQUEST),
    COMMENT_ALREADY_LIKED("COMMENT_ALREADY_LIKED", "Comment already liked", HttpStatus.CONFLICT),
    COMMENT_NOT_LIKED("COMMENT_NOT_LIKED", "Comment has not been liked", HttpStatus.CONFLICT),
    COMMENT_SLOW_MODE_ACTIVE("COMMENT_SLOW_MODE_ACTIVE", "Slow mode is active. Please wait before commenting again.", HttpStatus.TOO_MANY_REQUESTS),
    COMMENT_IDEMPOTENCY_CONFLICT("COMMENT_IDEMPOTENCY_CONFLICT", "Idempotency key reused with different request payload", HttpStatus.CONFLICT),
    POST_COMMENTING_RESTRICTED("POST_COMMENTING_RESTRICTED", "You do not have access to comment on this post", HttpStatus.FORBIDDEN),

    // Story
    STORY_NOT_FOUND("STORY_NOT_FOUND", "Story not found", HttpStatus.NOT_FOUND),
    STORY_FORBIDDEN("STORY_FORBIDDEN", "You do not have access to this story", HttpStatus.FORBIDDEN),

    // Message
    CONVERSATION_NOT_FOUND("CONVERSATION_NOT_FOUND", "Conversation not found", HttpStatus.NOT_FOUND),
    CONVERSATION_FORBIDDEN("CONVERSATION_FORBIDDEN", "You are not a participant of this conversation", HttpStatus.FORBIDDEN),
    CONVERSATION_INVALID_PARTICIPANTS("CONVERSATION_INVALID_PARTICIPANTS", "Invalid participant list", HttpStatus.BAD_REQUEST),
    CONVERSATION_NOT_GROUP("CONVERSATION_NOT_GROUP", "This operation requires a group conversation", HttpStatus.CONFLICT),
    GROUP_CHAT_DISABLED("GROUP_CHAT_DISABLED", "Group chat is not enabled", HttpStatus.FORBIDDEN),
    GROUP_ADMIN_REQUIRED("GROUP_ADMIN_REQUIRED", "Only a group admin may perform this action", HttpStatus.FORBIDDEN),
    PARTICIPANT_NOT_FOUND("PARTICIPANT_NOT_FOUND", "Participant not found in this conversation", HttpStatus.NOT_FOUND),
    MESSAGE_REQUEST_NOT_ALLOWED("MESSAGE_REQUEST_NOT_ALLOWED", "This user is not accepting message requests", HttpStatus.FORBIDDEN),
    MESSAGE_NOT_FOUND("MESSAGE_NOT_FOUND", "Message not found", HttpStatus.NOT_FOUND),
    MESSAGE_FORBIDDEN("MESSAGE_FORBIDDEN", "You do not have access to this message", HttpStatus.FORBIDDEN),
    MESSAGE_INVALID_PAYLOAD("MESSAGE_INVALID_PAYLOAD", "Message payload does not match its type", HttpStatus.UNPROCESSABLE_ENTITY),
    MESSAGE_IDEMPOTENCY_CONFLICT("MESSAGE_IDEMPOTENCY_CONFLICT", "Idempotency key reused with a different payload", HttpStatus.CONFLICT),

    // Report
    REPORT_NOT_FOUND("REPORT_NOT_FOUND", "Report not found", HttpStatus.NOT_FOUND),
    REPORT_TARGET_NOT_FOUND("REPORT_TARGET_NOT_FOUND", "Reported entity not found", HttpStatus.NOT_FOUND),
    REPORT_DUPLICATE("REPORT_DUPLICATE", "You have already reported this entity", HttpStatus.CONFLICT),
    REPORT_SELF_NOT_ALLOWED("REPORT_SELF_NOT_ALLOWED", "You cannot report your own content", HttpStatus.BAD_REQUEST),
    REPORT_INVALID_TRANSITION("REPORT_INVALID_TRANSITION", "Invalid report status transition", HttpStatus.CONFLICT),
    REPORT_RESOLUTION_NOTE_REQUIRED("REPORT_RESOLUTION_NOTE_REQUIRED", "A resolution note is required to close a report", HttpStatus.BAD_REQUEST),

    // Admin
    ADMIN_ACTION_NOT_FOUND("ADMIN_ACTION_NOT_FOUND", "Admin action not found", HttpStatus.NOT_FOUND),
    ADMIN_INVALID_ACTION("ADMIN_INVALID_ACTION", "Action is not valid for this target", HttpStatus.BAD_REQUEST),
    ADMIN_INVALID_TRANSITION("ADMIN_INVALID_TRANSITION", "Target is already in the requested moderation state", HttpStatus.CONFLICT);

    // spotless:on

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    ApiErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }
}
