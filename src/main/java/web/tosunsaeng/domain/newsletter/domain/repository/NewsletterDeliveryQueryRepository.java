package web.tosunsaeng.domain.newsletter.domain.repository;

import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterDelivery;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterFailureType;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NewsletterDeliveryQueryRepository {

    void upsertPendingDeliveries(
            String campaignId,
            String postId,
            Collection<String> subscriberIds,
            Instant now);

    Optional<NewsletterDelivery> claimNextPending(
            Instant now,
            Instant claimExpiresAt,
            String claimToken);

    Optional<NewsletterDelivery> claimNextRetry(
            Instant now,
            Instant claimExpiresAt,
            String claimToken,
            int maxAttempts);

    boolean releaseClaimToPending(
            String deliveryId,
            String claimToken,
            Instant now);

    Optional<NewsletterDelivery> markProviderCallStarted(
            String deliveryId,
            String claimToken,
            Instant now,
            Instant claimExpiresAt);

    boolean markSent(
            String deliveryId,
            String claimToken,
            String providerMessageId,
            Instant now);

    boolean markFailed(
            String deliveryId,
            String claimToken,
            NewsletterFailureType failureType,
            boolean retryable,
            Instant nextRetryAt,
            Instant now);

    boolean markSkipped(String deliveryId, String claimToken, Instant now);

    Optional<NewsletterDelivery> recoverNextStaleBeforeProvider(
            Instant now);

    Optional<NewsletterDelivery> failNextStaleAfterProvider(
            Instant now);

    Optional<NewsletterDelivery> requeueFailedForManualRetry(
            String deliveryId,
            int maxAttempts,
            Instant now);

    List<String> findManualRetryCandidateIds(
            String campaignId,
            int maxAttempts,
            int limit);

    boolean skipFailedInactive(
            String deliveryId,
            int maxAttempts,
            Instant now);

    DeliveryCounts countByCampaign(String campaignId, int maxAttempts);

    record DeliveryCounts(
            long total,
            long sent,
            long terminalFailed,
            long skipped) {

        public long terminalTotal() {
            return sent + terminalFailed + skipped;
        }
    }
}
