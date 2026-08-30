package com.app.modules.mail.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.app.modules.mail.entity.MailCampaignRecipient;

@Repository
public interface MailCampaignRecipientRepository
        extends JpaRepository<MailCampaignRecipient, UUID> {

    List<MailCampaignRecipient> findByCampaignId(UUID campaignId);

    void deleteByCampaignId(UUID campaignId);
}
