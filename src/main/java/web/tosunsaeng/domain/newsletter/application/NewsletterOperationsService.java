package web.tosunsaeng.domain.newsletter.application;

public interface NewsletterOperationsService {

    TestSendResult sendTest(String postId, String recipientEmail);

    boolean cancelScheduledCampaign(String campaignId);

    ManualRetryResult retryFailedDelivery(String deliveryId);

    enum TestSendResult {
        SENT,
        DISABLED
    }

    enum ManualRetryResult {
        REQUEUED,
        SKIPPED_INACTIVE,
        REJECTED
    }
}
