package com.app.modules.mail.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.GenericGenerator;

import com.app.modules.mail.converter.MailCampaignStatusConverter;
import com.app.modules.mail.enums.MailCampaignStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One campaign, and the record of what was actually sent.
 *
 * <p>{@code body} is a Markdown snapshot taken from whichever template the administrator started
 * from, and is immutable once the campaign leaves draft. Editing it afterwards would make the
 * record of what was sent a lie.
 */
@Entity
@Table(name = "mail_campaigns")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailCampaign {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Which sample this started from, for reporting only. */
    @Column(name = "template_key", length = 100)
    private String templateKey;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Convert(converter = MailCampaignStatusConverter.class)
    @Column(name = "status", nullable = false, columnDefinition = "mail_campaign_status")
    private MailCampaignStatus status;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
