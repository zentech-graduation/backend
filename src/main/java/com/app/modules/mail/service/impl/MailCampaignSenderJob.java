package com.app.modules.mail.service.impl;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.app.modules.mail.config.MailProperties;
import com.app.modules.mail.entity.EmailDelivery;
import com.app.modules.mail.entity.MailCampaign;
import com.app.modules.mail.entity.MailCampaignRecipient;
import com.app.modules.mail.enums.EmailDeliveryStatus;
import com.app.modules.mail.enums.MailCampaignRecipientStatus;
import com.app.modules.mail.enums.MailCampaignStatus;
import com.app.modules.mail.repository.EmailDeliveryRepository;
import com.app.modules.mail.repository.MailCampaignRecipientRepository;
import com.app.modules.mail.repository.MailCampaignRepository;
import com.app.modules.mail.service.MailUnsubscribeService;
import com.app.modules.users.entity.User;
import com.app.modules.users.entity.UserSettings;
import com.app.modules.users.repository.UserRepository;
import com.app.modules.users.repository.UserSettingsRepository;

/**
 * Sends campaigns whose scheduled time has arrived.
 *
 * <p>Nothing is sent inside an administrator's request. This job is the only sender.
 *
 * <p><strong>A double send is made impossible at the data layer, not by assumption.</strong> This
 * codebase has no distributed scheduler lock anywhere and assumes a single instance. For {@code
 * platform_stats} that assumption is harmless, because a composite key with {@code ON CONFLICT DO
 * UPDATE} makes a second run idempotent. Here a second run would mail real people twice, so the job
 * claims each campaign with a conditional UPDATE from {@code scheduled} to {@code sending} and
 * sends only when that update affected one row. Two instances racing produce one winner and one
 * no-op.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.support.campaign.sender",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class MailCampaignSenderJob {

    private static final Logger log = LoggerFactory.getLogger(MailCampaignSenderJob.class);

    private final MailCampaignRepository mailCampaignRepository;
    private final MailCampaignRecipientRepository mailCampaignRecipientRepository;
    private final EmailDeliveryRepository emailDeliveryRepository;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final CampaignBodyRenderer campaignBodyRenderer;
    private final AbstractTemplateMailSender mailSender;
    private final MailUnsubscribeService mailUnsubscribeService;
    private final MailProperties mailProperties;
    private final int batchSize;

    public MailCampaignSenderJob(
            MailCampaignRepository mailCampaignRepository,
            MailCampaignRecipientRepository mailCampaignRecipientRepository,
            EmailDeliveryRepository emailDeliveryRepository,
            UserRepository userRepository,
            UserSettingsRepository userSettingsRepository,
            CampaignBodyRenderer campaignBodyRenderer,
            AbstractTemplateMailSender mailSender,
            MailUnsubscribeService mailUnsubscribeService,
            MailProperties mailProperties,
            @Value("${app.support.campaign.sender.batch-size:20}") int batchSize) {
        this.mailCampaignRepository = mailCampaignRepository;
        this.mailCampaignRecipientRepository = mailCampaignRecipientRepository;
        this.emailDeliveryRepository = emailDeliveryRepository;
        this.userRepository = userRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.campaignBodyRenderer = campaignBodyRenderer;
        this.mailSender = mailSender;
        this.mailUnsubscribeService = mailUnsubscribeService;
        this.mailProperties = mailProperties;
        this.batchSize = batchSize;
    }

    @Scheduled(
            initialDelayString = "${app.support.campaign.sender.initial-delay:60s}",
            fixedDelayString = "${app.support.campaign.sender.fixed-delay:60s}")
    public void sendDueCampaigns() {
        List<MailCampaign> due =
                mailCampaignRepository.findDue(OffsetDateTime.now(ZoneOffset.UTC), batchSize);
        for (MailCampaign campaign : due) {
            // The claim is the whole safety property. Losing it means another instance is already
            // sending this campaign, so this one does nothing at all.
            if (mailCampaignRepository.claimForSending(campaign.getId()) == 0) {
                log.info("Campaign {} was already claimed by another instance", campaign.getId());
                continue;
            }
            try {
                send(campaign);
            } catch (RuntimeException ex) {
                log.error("Campaign {} failed to send: {}", campaign.getId(), ex.getMessage());
                markStatus(campaign.getId(), MailCampaignStatus.FAILED);
            }
        }
    }

    @Transactional
    protected void send(MailCampaign campaign) {
        List<MailCampaignRecipient> recipients =
                mailCampaignRecipientRepository.findByCampaignId(campaign.getId());
        int sent = 0;
        for (MailCampaignRecipient recipient : recipients) {
            // Re-read at send time rather than trusting the flag written at save time: a recipient
            // may have opted out between scheduling and sending, and the later answer is the one
            // that counts.
            if (isOptedOut(recipient.getUserId())) {
                recipient.setStatus(MailCampaignRecipientStatus.SKIPPED_OPTED_OUT);
                mailCampaignRecipientRepository.save(recipient);
                continue;
            }
            Optional<User> user = userRepository.findByIdAndDeletedAtIsNull(recipient.getUserId());
            if (user.isEmpty()) {
                recipient.setStatus(MailCampaignRecipientStatus.FAILED);
                mailCampaignRecipientRepository.save(recipient);
                continue;
            }
            sendOne(campaign, recipient, user.get());
            sent++;
        }
        campaign.setStatus(MailCampaignStatus.SENT);
        campaign.setSentAt(OffsetDateTime.now(ZoneOffset.UTC));
        mailCampaignRepository.save(campaign);
        if (sent == 0) {
            // Every recipient was excluded. The campaign is still 'sent' - it ran - but the
            // per-recipient rows say why nothing went out, which is what the administrator reads.
            log.info("Campaign {} sent to nobody; every recipient was excluded", campaign.getId());
        }
    }

    private void sendOne(MailCampaign campaign, MailCampaignRecipient recipient, User user) {
        Map<String, String> values = new HashMap<>();
        values.put("username", user.getUsername());
        values.put(
                "fullName",
                user.getDisplayName() == null ? user.getUsername() : user.getDisplayName());
        String html = campaignBodyRenderer.renderForRecipient(campaign.getBody(), values);

        EmailDelivery delivery =
                emailDeliveryRepository.save(
                        EmailDelivery.builder()
                                .recipientUserId(user.getId())
                                .recipientEmail(user.getEmail())
                                .templateKey("CAMPAIGN:" + campaign.getId())
                                .status(EmailDeliveryStatus.PENDING)
                                .attemptCount(0)
                                .build());
        try {
            String providerMessageId =
                    mailSender.sendCampaign(
                            campaign.getSubject(),
                            html,
                            unsubscribeUrl(user.getId()),
                            user.getEmail());
            delivery.setStatus(EmailDeliveryStatus.SENT);
            delivery.setProviderMessageId(providerMessageId);
            delivery.setSentAt(OffsetDateTime.now(ZoneOffset.UTC));
            recipient.setStatus(MailCampaignRecipientStatus.QUEUED);
        } catch (RuntimeException ex) {
            delivery.setStatus(EmailDeliveryStatus.FAILED);
            delivery.setErrorText(ex.getMessage());
            recipient.setStatus(MailCampaignRecipientStatus.FAILED);
        }
        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        emailDeliveryRepository.save(delivery);
        recipient.setResolvedEmail(user.getEmail());
        recipient.setEmailDeliveryId(delivery.getId());
        mailCampaignRecipientRepository.save(recipient);
    }

    // The link is per recipient and needs no session, because the person following it from a mail
    // client has none and may well be banned.
    private String unsubscribeUrl(UUID userId) {
        return org.springframework.web.util.UriComponentsBuilder.fromUriString(
                        mailProperties.getFrontendBaseUrl())
                .path("/support/unsubscribe")
                .queryParam("token", mailUnsubscribeService.tokenFor(userId))
                .build()
                .toUriString();
    }

    private boolean isOptedOut(UUID userId) {
        return userId != null
                && userSettingsRepository
                        .findById(userId)
                        .map(UserSettings::isEmailOptOut)
                        .orElse(false);
    }

    private void markStatus(UUID campaignId, MailCampaignStatus status) {
        mailCampaignRepository
                .findById(campaignId)
                .ifPresent(
                        campaign -> {
                            campaign.setStatus(status);
                            mailCampaignRepository.save(campaign);
                        });
    }
}
