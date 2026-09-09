package com.app.modules.mail.service;

import java.util.List;
import java.util.UUID;

import com.app.common.exception.AppException;
import com.app.modules.mail.dto.request.SaveMailCampaignRequest;
import com.app.modules.mail.dto.response.MailCampaignDetailResponse;
import com.app.modules.mail.dto.response.MailCampaignSummaryResponse;
import com.app.modules.mail.dto.response.MailCampaignTemplateResponse;

/**
 * Administrator-only custom mail campaigns.
 *
 * <p>A template is a sample. It is copied into the campaign on selection and never written back, so
 * a second administrator opening the same template gets the original.
 *
 * <p>Nothing is sent inside an administrator's request. A campaign is scheduled, and a background
 * job claims and sends it.
 */
public interface MailCampaignService {

    /** The read-only samples an administrator can start from. */
    List<MailCampaignTemplateResponse> listTemplates();

    /**
     * Renders a body through the one pipeline the send path uses.
     *
     * <p>Server-side on purpose. One implementation means the preview cannot diverge from the mail
     * that is actually sent, and there is no second sanitization surface in the browser.
     *
     * @param markdown the campaign body
     * @return sanitized HTML, with personalisation tokens left as tokens
     */
    String preview(String markdown);

    /**
     * Creates or replaces a draft campaign.
     *
     * @param actorId the acting administrator
     * @param request subject, body, recipients and optional schedule
     * @return the saved campaign
     * @throws AppException {@code CAMPAIGN_UNKNOWN_VARIABLE} naming an unpermitted token, {@code
     *     CAMPAIGN_TOO_MANY_RECIPIENTS} above ten, {@code CAMPAIGN_NO_RECIPIENTS} with none
     */
    MailCampaignDetailResponse createDraft(UUID actorId, SaveMailCampaignRequest request);

    /**
     * Updates a draft campaign.
     *
     * @param actorId the acting administrator
     * @param campaignId the campaign
     * @param request the new content
     * @return the saved campaign
     * @throws AppException {@code CAMPAIGN_NOT_EDITABLE} once the campaign has left draft, because
     *     the body is the record of what was sent
     */
    MailCampaignDetailResponse updateDraft(
            UUID actorId, UUID campaignId, SaveMailCampaignRequest request);

    /**
     * Moves a draft to scheduled.
     *
     * @param actorId the acting administrator
     * @param campaignId the campaign
     * @return the scheduled campaign
     * @throws AppException {@code CAMPAIGN_INVALID_TRANSITION} from any state but draft
     */
    MailCampaignDetailResponse schedule(UUID actorId, UUID campaignId);

    /** Campaign history, newest first. */
    List<MailCampaignSummaryResponse> listCampaigns(UUID actorId, int limit);

    /** One campaign with its recipients and their per-recipient outcome. */
    MailCampaignDetailResponse getCampaign(UUID actorId, UUID campaignId);
}
