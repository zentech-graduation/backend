package com.app.modules.mail.enums;

/**
 * Lifecycle of a campaign, mirroring the {@code mail_campaign_status} PostgreSQL enum.
 *
 * <p>{@code SENDING} is the claim state. The scheduler moves {@code SCHEDULED} to {@code SENDING}
 * with a conditional update, and that single-row transition is what makes a double send impossible:
 * this codebase has no distributed scheduler lock, and unlike {@code platform_stats} a second run
 * here would mail real people twice.
 */
public enum MailCampaignStatus {
    DRAFT,
    SCHEDULED,
    SENDING,
    SENT,
    CANCELLED,
    FAILED;

    /** Whether the body and recipients may still be edited. */
    public boolean isEditable() {
        return this == DRAFT;
    }
}
