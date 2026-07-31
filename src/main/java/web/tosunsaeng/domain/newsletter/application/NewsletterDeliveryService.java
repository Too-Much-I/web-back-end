package web.tosunsaeng.domain.newsletter.application;

public interface NewsletterDeliveryService {

    void processNextPendingDelivery();

    void processNextRetryDelivery();

    void recoverNextStaleDelivery();
}
