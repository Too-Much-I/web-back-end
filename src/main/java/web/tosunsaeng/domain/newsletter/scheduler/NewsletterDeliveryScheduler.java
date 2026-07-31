package web.tosunsaeng.domain.newsletter.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.newsletter.application.NewsletterDeliveryService;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Component
@Profile("!test")
public class NewsletterDeliveryScheduler {

    private final NewsletterDeliveryService deliveryService;
    private final Executor executor;

    public NewsletterDeliveryScheduler(
            NewsletterDeliveryService deliveryService,
            @Qualifier("newsletterTaskExecutor") Executor executor) {
        this.deliveryService = deliveryService;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${newsletter.delivery.delivery-delay-ms:5000}")
    public void processPendingDelivery() {
        submit(deliveryService::processNextPendingDelivery);
    }

    @Scheduled(fixedDelayString = "${newsletter.delivery.retry-delay-ms:30000}")
    public void processRetryDelivery() {
        submit(deliveryService::processNextRetryDelivery);
    }

    private void submit(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException exception) {
            log.warn("Newsletter delivery task was deferred because the bounded queue is full");
        }
    }
}
