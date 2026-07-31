package web.tosunsaeng.domain.newsletter.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.newsletter.application.NewsletterCampaignService;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Component
@Profile("!test")
public class NewsletterCampaignScheduler {

    private final NewsletterCampaignService campaignService;
    private final Executor executor;

    public NewsletterCampaignScheduler(
            NewsletterCampaignService campaignService,
            @Qualifier("newsletterTaskExecutor") Executor executor) {
        this.campaignService = campaignService;
        this.executor = executor;
    }

    @Scheduled(
            fixedDelayString =
                    "${newsletter.delivery.reconciliation-delay-ms:60000}")
    public void reconcileCampaigns() {
        submit(campaignService::reconcileCampaigns);
    }

    @Scheduled(fixedDelayString = "${newsletter.delivery.campaign-delay-ms:10000}")
    public void processScheduledCampaign() {
        submit(campaignService::processNextScheduledCampaign);
    }

    private void submit(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException exception) {
            log.warn("Newsletter campaign task was deferred because the bounded queue is full");
        }
    }
}
