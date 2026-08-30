package com.app.modules.mail.enums;

/**
 * Per-recipient outcome, mirroring the {@code mail_campaign_recipient_status} PostgreSQL enum.
 *
 * <p>{@code SKIPPED_OPTED_OUT} records an exclusion rather than dropping the row, so an
 * administrator can see who was left out and why instead of wondering where a recipient went.
 */
public enum MailCampaignRecipientStatus {
    PENDING,
    QUEUED,
    SKIPPED_OPTED_OUT,
    FAILED
}
