package web.tosunsaeng.domain.newsletter.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.newsletter.application.NewsletterCampaignService;
import web.tosunsaeng.domain.newsletter.application.NewsletterDeliveryService;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Component
@Profile("!test")
public class NewsletterMaintenanceScheduler {

    private final NewsletterCampaignService campaignService;
    private final NewsletterDeliveryService deliveryService;
    private final Executor executor;

    public NewsletterMaintenanceScheduler(
            NewsletterCampaignService campaignService,
            NewsletterDeliveryService deliveryService,
            @Qualifier("newsletterTaskExecutor") Executor executor) {
        this.campaignService = campaignService;
        this.deliveryService = deliveryService;
        this.executor = executor;
    }

    @Scheduled(
            fixedDelayString =
                    "${newsletter.delivery.stale-recovery-delay-ms:60000}")
    public void recoverStaleClaims() {
        submit(campaignService::processNextStaleCampaign);
        submit(deliveryService::recoverNextStaleDelivery);
    }

    @Scheduled(fixedDelayString = "${newsletter.delivery.completion-delay-ms:30000}")
    public void completeCampaign() {
        submit(campaignService::completeNextCampaign);
    }

    private void submit(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException exception) {
            log.warn("Newsletter maintenance task was deferred because the bounded queue is full");
        }
    }
}
