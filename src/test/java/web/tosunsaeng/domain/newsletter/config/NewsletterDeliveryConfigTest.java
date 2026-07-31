package web.tosunsaeng.domain.newsletter.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;

import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class NewsletterDeliveryConfigTest {

    @Test
    void executorHasFixedWorkersBoundedQueueAbortBackpressureAndNewsletterPrefix() {
        NewsletterDeliveryProperties properties = new NewsletterDeliveryProperties();
        NewsletterDeliveryConfig config = new NewsletterDeliveryConfig();
        ThreadPoolTaskExecutor executor = config.newsletterTaskExecutor(properties);
        executor.afterPropertiesSet();
        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(2);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("newsletter-");
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity())
                    .isEqualTo(100);
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void sesClientHasFiniteTimeoutAndNoSdkLevelRetries() {
        ClientOverrideConfiguration configuration =
                NewsletterDeliveryConfig.sesClientOverrideConfiguration();

        assertThat(configuration.apiCallTimeout()).contains(Duration.ofSeconds(15));
        assertThat(configuration.apiCallAttemptTimeout()).contains(Duration.ofSeconds(10));
        assertThat(configuration.retryPolicy()).isPresent();
        assertThat(configuration.retryPolicy().orElseThrow().numRetries()).isZero();
    }
}
