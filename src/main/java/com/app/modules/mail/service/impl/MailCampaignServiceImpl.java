package com.app.modules.mail.service.impl;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.admin.service.AdminAuthorizationService;
import com.app.modules.mail.dto.request.SaveMailCampaignRequest;
import com.app.modules.mail.dto.response.MailCampaignDetailResponse;
import com.app.modules.mail.dto.response.MailCampaignRecipientResponse;
import com.app.modules.mail.dto.response.MailCampaignSummaryResponse;
import com.app.modules.mail.dto.response.MailCampaignTemplateResponse;
import com.app.modules.mail.entity.MailCampaign;
import com.app.modules.mail.entity.MailCampaignRecipient;
import com.app.modules.mail.enums.MailCampaignRecipientStatus;
import com.app.modules.mail.enums.MailCampaignStatus;
import com.app.modules.mail.repository.MailCampaignRecipientRepository;
import com.app.modules.mail.repository.MailCampaignRepository;
import com.app.modules.mail.repository.MailCampaignTemplateRepository;
import com.app.modules.mail.service.MailCampaignService;
import com.app.modules.users.repository.UserRepository;
import com.app.modules.users.repository.UserSettingsRepository;

@Service
public class MailCampaignServiceImpl implements MailCampaignService {

    /**
     * The recipient cap.
     *
     * <p>Ten, because this release has no segment queries and a hand-picked list is the only way to
     * choose recipients. A cap this low is what keeps an accidental send small enough to apologise
     * for.
     */
    private static final int MAX_RECIPIENTS = 10;

    private static final int MAX_PAGE_SIZE = 100;

    private final MailCampaignRepository mailCampaignRepository;
    private final MailCampaignRecipientRepository mailCampaignRecipientRepository;
    private final MailCampaignTemplateRepository mailCampaignTemplateRepository;
    private final CampaignBodyRenderer campaignBodyRenderer;
    private final AdminAuthorizationService adminAuthorizationService;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;

    public MailCampaignServiceImpl(
            MailCampaignRepository mailCampaignRepository,
            MailCampaignRecipientRepository mailCampaignRecipientRepository,
            MailCampaignTemplateRepository mailCampaignTemplateRepository,
            CampaignBodyRenderer campaignBodyRenderer,
            AdminAuthorizationService adminAuthorizationService,
            UserRepository userRepository,
            UserSettingsRepository userSettingsRepository) {
        this.mailCampaignRepository = mailCampaignRepository;
        this.mailCampaignRecipientRepository = mailCampaignRecipientRepository;
        this.mailCampaignTemplateRepository = mailCampaignTemplateRepository;
        this.campaignBodyRenderer = campaignBodyRenderer;
        this.adminAuthorizationService = adminAuthorizationService;
        this.userRepository = userRepository;
        this.userSettingsRepository = userSettingsRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MailCampaignTemplateResponse> listTemplates() {
        return mailCampaignTemplateRepository.findEnabled().stream()
                .map(
                        template ->
                                new MailCampaignTemplateResponse(
                                        template.getTemplateKey(),
                                        template.getDisplayName(),
                                        template.getDescription(),
                                        template.getBody()))
                .toList();
    }

    @Override
    public String preview(String markdown) {
        return campaignBodyRenderer.renderSanitized(markdown);
    }

    @Override
    @Transactional
    public MailCampaignDetailResponse createDraft(UUID actorId, SaveMailCampaignRequest request) {
        adminAuthorizationService.assertActorIsAdministrator(actorId);
        List<UUID> recipients = validate(request);
        MailCampaign campaign =
                mailCampaignRepository.save(
                        MailCampaign.builder()
                                .templateKey(request.templateKey())
                                .subject(request.subject())
                                .body(request.body())
                                .createdBy(actorId)
                                .status(MailCampaignStatus.DRAFT)
                                .scheduledAt(request.scheduledAt())
                                .recipientCount(recipients.size())
                                .build());
        replaceRecipients(campaign.getId(), recipients);
        return detail(campaign);
    }

    @Override
    @Transactional
    public MailCampaignDetailResponse updateDraft(
            UUID actorId, UUID campaignId, SaveMailCampaignRequest request) {
        adminAuthorizationService.assertActorIsAdministrator(actorId);
        MailCampaign campaign = requireCampaign(campaignId);
        // The body is the record of what was sent. Once the campaign leaves draft, editing it would
        // make that record disagree with the mail people actually received.
        if (!campaign.getStatus().isEditable()) {
            throw new AppException(ApiErrorCode.CAMPAIGN_NOT_EDITABLE);
        }
        List<UUID> recipients = validate(request);
        campaign.setTemplateKey(request.templateKey());
        campaign.setSubject(request.subject());
        campaign.setBody(request.body());
        campaign.setScheduledAt(request.scheduledAt());
        campaign.setRecipientCount(recipients.size());
        MailCampaign saved = mailCampaignRepository.save(campaign);
        replaceRecipients(saved.getId(), recipients);
        return detail(saved);
    }

    @Override
    @Transactional
    public MailCampaignDetailResponse schedule(UUID actorId, UUID campaignId) {
        adminAuthorizationService.assertActorIsAdministrator(actorId);
        MailCampaign campaign = requireCampaign(campaignId);
        if (campaign.getStatus() != MailCampaignStatus.DRAFT) {
            throw new AppException(ApiErrorCode.CAMPAIGN_INVALID_TRANSITION);
        }
        if (campaign.getScheduledAt() == null) {
            campaign.setScheduledAt(OffsetDateTime.now(ZoneOffset.UTC));
        }
        campaign.setStatus(MailCampaignStatus.SCHEDULED);
        return detail(mailCampaignRepository.save(campaign));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MailCampaignSummaryResponse> listCampaigns(UUID actorId, int limit) {
        adminAuthorizationService.assertActorIsAdministrator(actorId);
        return mailCampaignRepository
                .findHistory(PageRequest.of(0, Math.max(1, Math.min(limit, MAX_PAGE_SIZE))))
                .stream()
                .map(
                        campaign ->
                                new MailCampaignSummaryResponse(
                                        campaign.getId(),
                                        campaign.getSubject(),
                                        campaign.getStatus(),
                                        campaign.getRecipientCount(),
                                        campaign.getCreatedBy(),
                                        campaign.getScheduledAt(),
                                        campaign.getSentAt(),
                                        campaign.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MailCampaignDetailResponse getCampaign(UUID actorId, UUID campaignId) {
        adminAuthorizationService.assertActorIsAdministrator(actorId);
        return detail(requireCampaign(campaignId));
    }

    private List<UUID> validate(SaveMailCampaignRequest request) {
        // Validated here, at save time, and never at send time. A campaign that saved must not fail
        // later with the mail half-sent because of a token nobody checked.
        campaignBodyRenderer.validateVariables(request.body());

        // Deduplicated before the cap is applied, so eleven entries naming ten distinct accounts is
        // accepted rather than refused on a technicality.
        List<UUID> recipients =
                new LinkedHashSet<>(
                                request.recipientUserIds() == null
                                        ? List.<UUID>of()
                                        : request.recipientUserIds())
                        .stream().toList();
        if (recipients.isEmpty()) {
            throw new AppException(ApiErrorCode.CAMPAIGN_NO_RECIPIENTS);
        }
        if (recipients.size() > MAX_RECIPIENTS) {
            throw new AppException(ApiErrorCode.CAMPAIGN_TOO_MANY_RECIPIENTS);
        }
        return recipients;
    }

    private void replaceRecipients(UUID campaignId, List<UUID> recipientUserIds) {
        mailCampaignRecipientRepository.deleteByCampaignId(campaignId);
        // Opted-out recipients are identified now, at save time, so the administrator sees who will
        // be skipped before scheduling rather than discovering it in the sent report.
        Set<UUID> optedOut =
                Set.copyOf(userSettingsRepository.findOptedOutUserIds(recipientUserIds));
        mailCampaignRecipientRepository.saveAll(
                recipientUserIds.stream()
                        .map(
                                userId ->
                                        MailCampaignRecipient.builder()
                                                .campaignId(campaignId)
                                                .userId(userId)
                                                .status(
                                                        optedOut.contains(userId)
                                                                ? MailCampaignRecipientStatus
                                                                        .SKIPPED_OPTED_OUT
                                                                : MailCampaignRecipientStatus
                                                                        .PENDING)
                                                .build())
                        .toList());
    }

    private MailCampaignDetailResponse detail(MailCampaign campaign) {
        List<MailCampaignRecipient> rows =
                mailCampaignRecipientRepository.findByCampaignId(campaign.getId());
        // One lookup per recipient rather than a batch read. UserRepository extends Repository, not
        // JpaRepository, and that narrow surface is guarded by UserRepositorySurfaceTest; widening
        // it to save nine queries on a list capped at ten would be the wrong trade.
        Map<UUID, String> usernames = new java.util.HashMap<>();
        for (MailCampaignRecipient row : rows) {
            userRepository
                    .findByIdAndDeletedAtIsNull(row.getUserId())
                    .ifPresent(user -> usernames.put(user.getId(), user.getUsername()));
        }
        return new MailCampaignDetailResponse(
                campaign.getId(),
                campaign.getTemplateKey(),
                campaign.getSubject(),
                campaign.getBody(),
                campaign.getStatus(),
                campaign.getRecipientCount(),
                campaign.getCreatedBy(),
                campaign.getScheduledAt(),
                campaign.getSentAt(),
                campaign.getCreatedAt(),
                rows.stream()
                        .map(
                                row ->
                                        new MailCampaignRecipientResponse(
                                                row.getUserId(),
                                                usernames.get(row.getUserId()),
                                                row.getResolvedEmail(),
                                                row.getStatus()))
                        .sorted(
                                java.util.Comparator.comparing(
                                        MailCampaignRecipientResponse::username,
                                        java.util.Comparator.nullsLast(
                                                java.util.Comparator.naturalOrder())))
                        .toList());
    }

    private MailCampaign requireCampaign(UUID campaignId) {
        return mailCampaignRepository
                .findById(campaignId)
                .orElseThrow(() -> new AppException(ApiErrorCode.CAMPAIGN_NOT_FOUND));
    }
}
