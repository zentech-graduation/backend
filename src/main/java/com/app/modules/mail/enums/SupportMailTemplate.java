package com.app.modules.mail.enums;

import lombok.Getter;

/**
 * Support mail that is neither auth mail nor a moderation notice.
 *
 * <p>Kept as its own enum rather than folded into {@link ModerationMailTemplate}, because a
 * confirmation link is not a moderation decision: it carries no community-standards line, it is
 * sent to an address the platform has not yet proved anything about, and it is the only mail here
 * sent outside the queue.
 */
@Getter
public enum SupportMailTemplate {
    CONFIRM_SUPPORT_REQUEST("mail/support/confirm-support-request", "Confirm your support request");

    private final String templatePath;
    private final String defaultSubject;

    SupportMailTemplate(String templatePath, String defaultSubject) {
        this.templatePath = templatePath;
        this.defaultSubject = defaultSubject;
    }
}
