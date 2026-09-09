package com.app.modules.mail.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.app.modules.mail.entity.MailCampaignTemplate;

@Repository
public interface MailCampaignTemplateRepository
        extends JpaRepository<MailCampaignTemplate, String> {

    /**
     * Every enabled sample, in display order.
     *
     * <p>Ordered by {@code sort_order} then key, so two rows sharing a sort order still come back
     * in a stable order rather than whichever the heap yields.
     *
     * @return the enabled templates
     */
    @Query(
            "SELECT t FROM MailCampaignTemplate t WHERE t.enabled = true"
                    + " ORDER BY t.sortOrder ASC, t.templateKey ASC")
    List<MailCampaignTemplate> findEnabled();
}
