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

import com.app.modules.mail.converter.MailCampaignRecipientStatusConverter;
import com.app.modules.mail.enums.MailCampaignRecipientStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One recipient of one campaign, including one deliberately excluded for opting out. */
@Entity
@Table(name = "mail_campaign_recipients")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailCampaignRecipient {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "campaign_id", nullable = false, updatable = false)
    private UUID campaignId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    /** The address as it stood at send time, not at draft time. Null until the send reads it. */
    @Column(name = "resolved_email", length = 255)
    private String resolvedEmail;

    @Convert(converter = MailCampaignRecipientStatusConverter.class)
    @Column(name = "status", nullable = false, columnDefinition = "mail_campaign_recipient_status")
    private MailCampaignRecipientStatus status;

    @Column(name = "email_delivery_id")
    private UUID emailDeliveryId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
