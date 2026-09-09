package com.app.modules.mail.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.modules.mail.entity.MailCampaign;

@Repository
public interface MailCampaignRepository extends JpaRepository<MailCampaign, UUID> {

    /**
     * Claims one due campaign for sending, atomically.
     *
     * <p>This single conditional UPDATE is what makes a double send impossible. There is no
     * distributed scheduler lock anywhere in this codebase and a single instance is assumed; for
     * {@code platform_stats} that assumption is harmless, because a composite key with {@code ON
     * CONFLICT DO UPDATE} makes a second run idempotent. Here a second run would mail real people
     * twice, so the claim is enforced at the data layer instead of by the assumption.
     *
     * <p>Two schedulers racing produce one update of 1 and one of 0. Only the winner sends.
     *
     * @param campaignId the campaign to claim
     * @return 1 when this call claimed it, 0 when another instance already had
     */
    @Modifying
    @Query(
            value =
                    "UPDATE mail_campaigns SET status = 'sending' WHERE id = :campaignId"
                            + " AND status = 'scheduled'",
            nativeQuery = true)
    int claimForSending(@Param("campaignId") UUID campaignId);

    /**
     * Campaigns whose scheduled time has arrived, oldest first. Served by idx_mail_campaigns_due.
     */
    @Query(
            value =
                    "SELECT * FROM mail_campaigns WHERE status = 'scheduled'"
                            + " AND scheduled_at <= :now ORDER BY scheduled_at ASC LIMIT :batchSize",
            nativeQuery = true)
    List<MailCampaign> findDue(@Param("now") OffsetDateTime now, @Param("batchSize") int batchSize);

    /** Campaign history, newest first. */
    @Query("SELECT c FROM MailCampaign c ORDER BY c.createdAt DESC, c.id DESC")
    List<MailCampaign> findHistory(Pageable pageable);
}
