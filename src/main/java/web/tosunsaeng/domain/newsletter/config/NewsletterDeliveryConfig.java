package web.tosunsaeng.domain.newsletter.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NewsletterDeliveryProperties.class)
public class NewsletterDeliveryConfig {

    @Bean(name = "newsletterTaskExecutor")
    public ThreadPoolTaskExecutor newsletterTaskExecutor(
            NewsletterDeliveryProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getWorkerCount());
        executor.setMaxPoolSize(properties.getWorkerCount());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix("newsletter-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Bean(destroyMethod = "close")
    @Profile("!local & !test")
    @ConditionalOnProperty(
            prefix = "newsletter.delivery",
            name = "email-provider",
            havingValue = "ses")
    public SesV2Client newsletterSesV2Client(
            NewsletterDeliveryProperties properties) {
        return SesV2Client.builder()
                .region(Region.of(properties.getAwsRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .overrideConfiguration(sesClientOverrideConfiguration())
                .build();
    }

    static ClientOverrideConfiguration sesClientOverrideConfiguration() {
        return ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(15))
                .apiCallAttemptTimeout(Duration.ofSeconds(10))
                .retryPolicy(RetryPolicy.builder().numRetries(0).build())
                .build();
    }
}
