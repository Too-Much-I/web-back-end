package web.tosunsaeng.domain.newsletter.domain.repository;

import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterCampaign;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterCampaignStatus;

import java.time.Instant;
import java.util.Optional;

public interface NewsletterCampaignQueryRepository {

    Optional<NewsletterCampaign> claimNextScheduled(
            Instant now,
            Instant claimExpiresAt,
            String claimToken);

    Optional<NewsletterCampaign> reclaimNextExpiredGeneration(
            Instant now,
            Instant claimExpiresAt,
            String claimToken);

    boolean extendGenerationClaim(
            String campaignId,
            String claimToken,
            Instant claimExpiresAt,
            Instant now);

    boolean finishDeliveryGeneration(
            String campaignId,
            String claimToken,
            long totalRecipients,
            Instant now);

    Optional<NewsletterCampaign> findNextReadyForCompletion();

    boolean touchSendingCampaign(String campaignId, String claimToken, Instant now);

    boolean completeCampaign(
            String campaignId,
            String claimToken,
            NewsletterCampaignStatus terminalStatus,
            long totalRecipients,
            long sentCount,
            long failedCount,
            long skippedCount,
            Instant now);

    boolean cancelScheduled(String campaignId, Instant now);

    boolean reopenFailed(String campaignId, Instant now);
}
