package web.tosunsaeng.domain.newsletter.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import web.tosunsaeng.domain.newsletter.application.NewsletterCampaignService;
import web.tosunsaeng.domain.newsletter.application.NewsletterDeliveryService;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NewsletterSchedulersTest {

    @Mock
    private NewsletterCampaignService campaignService;

    @Mock
    private NewsletterDeliveryService deliveryService;

    @Test
    void campaignSchedulerSubmitsWorkBeforeAnyDatabaseClaimOccurs() {
        CapturingExecutor executor = new CapturingExecutor();
        NewsletterCampaignScheduler scheduler =
                new NewsletterCampaignScheduler(campaignService, executor);

        scheduler.reconcileCampaigns();
        scheduler.processScheduledCampaign();

        verifyNoInteractions(campaignService);
        assertThat(executor.tasks).hasSize(2);
        executor.tasks.forEach(Runnable::run);
        verify(campaignService).reconcileCampaigns();
        verify(campaignService).processNextScheduledCampaign();
    }

    @Test
    void deliveryAndMaintenanceSchedulersDelegateAllSeparateRoles() {
        Executor direct = Runnable::run;
        NewsletterDeliveryScheduler deliveryScheduler =
                new NewsletterDeliveryScheduler(deliveryService, direct);
        NewsletterMaintenanceScheduler maintenanceScheduler =
                new NewsletterMaintenanceScheduler(
                        campaignService, deliveryService, direct);

        deliveryScheduler.processPendingDelivery();
        deliveryScheduler.processRetryDelivery();
        maintenanceScheduler.recoverStaleClaims();
        maintenanceScheduler.completeCampaign();

        verify(deliveryService).processNextPendingDelivery();
        verify(deliveryService).processNextRetryDelivery();
        verify(deliveryService).recoverNextStaleDelivery();
        verify(campaignService).processNextStaleCampaign();
        verify(campaignService).completeNextCampaign();
    }

    @Test
    void boundedQueueRejectionDefersWorkWithoutCallingServiceOrThrowing() {
        Executor rejecting = command -> {
            throw new RejectedExecutionException("bounded queue full");
        };
        NewsletterCampaignScheduler scheduler =
                new NewsletterCampaignScheduler(campaignService, rejecting);

        assertThatCode(scheduler::processScheduledCampaign).doesNotThrowAnyException();

        verifyNoInteractions(campaignService);
    }

    @Test
    void everyScheduledMethodUsesConfiguredFixedDelayAndNoFixedRate() throws Exception {
        assertFixedDelay(
                NewsletterCampaignScheduler.class,
                "reconcileCampaigns",
                "${newsletter.delivery.reconciliation-delay-ms:60000}");
        assertFixedDelay(
                NewsletterCampaignScheduler.class,
                "processScheduledCampaign",
                "${newsletter.delivery.campaign-delay-ms:10000}");
        assertFixedDelay(
                NewsletterDeliveryScheduler.class,
                "processPendingDelivery",
                "${newsletter.delivery.delivery-delay-ms:5000}");
        assertFixedDelay(
                NewsletterDeliveryScheduler.class,
                "processRetryDelivery",
                "${newsletter.delivery.retry-delay-ms:30000}");
        assertFixedDelay(
                NewsletterMaintenanceScheduler.class,
                "recoverStaleClaims",
                "${newsletter.delivery.stale-recovery-delay-ms:60000}");
        assertFixedDelay(
                NewsletterMaintenanceScheduler.class,
                "completeCampaign",
                "${newsletter.delivery.completion-delay-ms:30000}");
    }

    @Test
    void schedulersAreDisabledInTestProfileToAvoidRealMongoBackgroundWork() {
        assertThat(NewsletterCampaignScheduler.class.getAnnotation(Profile.class).value())
                .containsExactly("!test");
        assertThat(NewsletterDeliveryScheduler.class.getAnnotation(Profile.class).value())
                .containsExactly("!test");
        assertThat(NewsletterMaintenanceScheduler.class.getAnnotation(Profile.class).value())
                .containsExactly("!test");
    }

    private void assertFixedDelay(
            Class<?> schedulerType,
            String methodName,
            String expectedExpression) throws Exception {
        Method method = schedulerType.getDeclaredMethod(methodName);
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString()).isEqualTo(expectedExpression);
        assertThat(scheduled.fixedRate()).isEqualTo(-1L);
        assertThat(scheduled.fixedRateString()).isEmpty();
    }

    private static final class CapturingExecutor implements Executor {

        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }
    }
}
