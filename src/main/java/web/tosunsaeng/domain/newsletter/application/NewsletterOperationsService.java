package web.tosunsaeng.domain.newsletter.application;

public interface NewsletterOperationsService {

    TestSendResult sendTest(String postId, String recipientEmail);

    boolean cancelScheduledCampaign(String campaignId);

    void cancelScheduledCampaignByPostId(String postId);

    ManualRetryResult retryFailedDelivery(String deliveryId);

    ManualRetryBatchResult retryFailedDeliveriesByPostId(String postId);

    enum TestSendResult {
        SENT,
        DISABLED
    }

    enum ManualRetryResult {
        REQUEUED,
        SKIPPED_INACTIVE,
        REJECTED
    }

    record ManualRetryBatchResult(
            int retriedCount,
            int skippedCount,
            boolean hasMore) {
    }
}
