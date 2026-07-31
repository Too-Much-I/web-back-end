package web.tosunsaeng.integration.mongo;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import web.tosunsaeng.domain.blog.config.BlogPostMongoIndexInitializer;
import web.tosunsaeng.domain.blog.domain.entity.BlogPost;
import web.tosunsaeng.domain.blog.domain.enums.BlogPostStatus;
import web.tosunsaeng.domain.blog.domain.repository.BlogPostRepository;
import web.tosunsaeng.domain.newsletter.application.NewsletterCampaignServiceImpl;
import web.tosunsaeng.domain.newsletter.application.NewsletterDeliveryServiceImpl;
import web.tosunsaeng.domain.newsletter.config.NewsletterDeliveryProperties;
import web.tosunsaeng.domain.newsletter.config.NewsletterMongoIndexInitializer;
import web.tosunsaeng.domain.newsletter.config.NewsletterUnsubscribeTokenProperties;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterCampaign;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterDelivery;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterSubscriber;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterCampaignStatus;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterDeliveryStatus;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterFailureType;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterSubscriberStatus;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterClaimTokenGenerator;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterEmailNormalizer;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterEmailTemplateRenderer;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterLinkBuilder;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterRetryPolicy;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterUnsubscribeTokenManager;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterCampaignRepository;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterDeliveryRepository;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterSubscriberRepository;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailMessage;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailSendException;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailSendResult;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailSender;
import web.tosunsaeng.integration.support.IntegrationContainers;
import web.tosunsaeng.integration.support.MutableUtcClock;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@ActiveProfiles("test")
@Testcontainers
class NewsletterSchedulerMongoIntegrationTest {

    private static final String DATABASE = "phase08_scheduler";
    private static final Instant NOW = Instant.parse("2026-07-31T07:00:00Z");

    @Container
    static final MongoDBContainer MONGO = IntegrationContainers.mongo();

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.database", () -> DATABASE);
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private BlogPostRepository blogPostRepository;

    @Autowired
    private NewsletterCampaignRepository campaignRepository;

    @Autowired
    private NewsletterDeliveryRepository deliveryRepository;

    @Autowired
    private NewsletterSubscriberRepository subscriberRepository;

    @BeforeEach
    void resetDatabase() {
        mongoTemplate.getDb().drop();
        new BlogPostMongoIndexInitializer(mongoTemplate).run(null);
        new NewsletterMongoIndexInitializer(mongoTemplate).run(null);
    }

    @Test
    void optInReconciliationBatchGenerationAndKillSwitchResumeAreDeterministic()
            throws Exception {
        blogPostRepository.saveAll(List.of(
                post("past", "past-post", BlogPostStatus.PUBLISHED,
                        NOW.minus(Duration.ofMinutes(30)), true),
                post("boundary", "boundary-post", BlogPostStatus.PUBLISHED,
                        NOW, true),
                post("disabled", "disabled-post", BlogPostStatus.PUBLISHED,
                        NOW.minusSeconds(1), false),
                post("draft", "draft-post", BlogPostStatus.DRAFT,
                        NOW.minusSeconds(1), true),
                post("archived", "archived-post", BlogPostStatus.ARCHIVED,
                        NOW.minusSeconds(1), true),
                post("null-date", "null-date", BlogPostStatus.PUBLISHED,
                        null, true)));
        mongoTemplate.getCollection("blog_posts").insertOne(new Document()
                .append("_id", "missing-opt-in")
                .append("slug", "missing-opt-in")
                .append("title", "기존 글")
                .append("status", BlogPostStatus.PUBLISHED.name())
                .append("publishedAt", Date.from(NOW.minusSeconds(1)))
                .append("createdAt", Date.from(NOW.minusSeconds(1)))
                .append("updatedAt", Date.from(NOW.minusSeconds(1))));
        List<NewsletterSubscriber> subscribers = new ArrayList<>();
        for (int index = 0; index < 205; index++) {
            subscribers.add(subscriber("subscriber-%03d".formatted(index),
                    NewsletterSubscriberStatus.ACTIVE));
        }
        subscriberRepository.saveAll(subscribers);

        MutableUtcClock clock = new MutableUtcClock(NOW);
        NewsletterDeliveryProperties properties = properties(false);
        NewsletterCampaignServiceImpl campaignService = campaignService(properties, clock);
        DeterministicSender sender = new DeterministicSender();
        NewsletterDeliveryServiceImpl deliveryService = deliveryService(
                properties, clock, sender);

        campaignService.reconcileCampaigns();
        campaignService.reconcileCampaigns();

        assertThat(campaignRepository.findAll()).hasSize(2);
        assertThat(campaignRepository.findByPostId("past").orElseThrow().getScheduledAt())
                .isEqualTo(NOW);
        assertThat(campaignRepository.findByPostId("boundary").orElseThrow().getScheduledAt())
                .isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        campaignService.processNextScheduledCampaign();
        assertThat(deliveryRepository.count()).isZero();

        properties.setSendingEnabled(true);
        race(campaignService::processNextScheduledCampaign,
                campaignService::processNextScheduledCampaign);

        NewsletterCampaign generated = campaignRepository.findByPostId("past")
                .orElseThrow();
        assertThat(generated.getStatus()).isEqualTo(NewsletterCampaignStatus.SENDING);
        assertThat(generated.getTotalRecipients()).isEqualTo(205);
        assertThat(deliveryRepository.count()).isEqualTo(205);
        assertThat(deliveryRepository.findAll())
                .extracting(NewsletterDelivery::getSubscriberId)
                .doesNotHaveDuplicates();

        properties.setSendingEnabled(false);
        deliveryService.processNextPendingDelivery();
        assertThat(sender.calls()).isZero();
        properties.setSendingEnabled(true);
        deliveryService.processNextPendingDelivery();
        assertThat(sender.calls()).isEqualTo(1);
        assertThat(deliveryRepository.findAll())
                .filteredOn(delivery -> delivery.getStatus() == NewsletterDeliveryStatus.SENT)
                .hasSize(1);
    }

    @Test
    void staleBoundaryAndInactiveSubscriberBecomePendingUnknownAndSkipped() {
        blogPostRepository.insert(post(
                "post-1", "post-one", BlogPostStatus.PUBLISHED,
                NOW.minusSeconds(60), true));
        campaignRepository.insert(campaign("campaign-1", "post-1", 3L));
        subscriberRepository.saveAll(List.of(
                subscriber("inactive", NewsletterSubscriberStatus.UNSUBSCRIBED),
                subscriber("before", NewsletterSubscriberStatus.ACTIVE),
                subscriber("after", NewsletterSubscriberStatus.ACTIVE)));
        deliveryRepository.saveAll(List.of(
                delivery("a-inactive", "campaign-1", "post-1", "inactive",
                        NewsletterDeliveryStatus.PENDING, 0, null, null, null),
                delivery("before-stale", "campaign-1", "post-1", "before",
                        NewsletterDeliveryStatus.SENDING, 0,
                        "before-token", NOW.minusSeconds(1), null),
                delivery("after-stale", "campaign-1", "post-1", "after",
                        NewsletterDeliveryStatus.SENDING, 1,
                        "after-token", NOW.minusSeconds(1), NOW.minusSeconds(30))));
        MutableUtcClock clock = new MutableUtcClock(NOW);
        NewsletterDeliveryProperties properties = properties(true);
        DeterministicSender sender = new DeterministicSender();
        NewsletterDeliveryServiceImpl deliveryService = deliveryService(
                properties, clock, sender);

        deliveryService.processNextPendingDelivery();
        deliveryService.recoverNextStaleDelivery();

        assertThat(deliveryRepository.findById("a-inactive").orElseThrow().getStatus())
                .isEqualTo(NewsletterDeliveryStatus.SKIPPED);
        assertThat(deliveryRepository.findById("before-stale").orElseThrow().getStatus())
                .isEqualTo(NewsletterDeliveryStatus.PENDING);
        NewsletterDelivery unknown = deliveryRepository.findById("after-stale")
                .orElseThrow();
        assertThat(unknown.getStatus()).isEqualTo(NewsletterDeliveryStatus.FAILED);
        assertThat(unknown.getLastErrorCode())
                .isEqualTo(NewsletterFailureType.PROVIDER_RESULT_UNKNOWN);
        assertThat(unknown.isRetryable()).isFalse();
        assertThat(sender.calls()).isZero();
    }

    @Test
    void retryStopsAfterFourProviderCallsAndCampaignAggregatesFailure() {
        blogPostRepository.insert(post(
                "post-1", "post-one", BlogPostStatus.PUBLISHED,
                NOW.minusSeconds(60), true));
        campaignRepository.insert(campaign("campaign-1", "post-1", 1L));
        subscriberRepository.insert(subscriber(
                "subscriber-1", NewsletterSubscriberStatus.ACTIVE));
        deliveryRepository.insert(delivery(
                "delivery-1", "campaign-1", "post-1", "subscriber-1",
                NewsletterDeliveryStatus.PENDING, 0, null, null, null));
        MutableUtcClock clock = new MutableUtcClock(NOW);
        NewsletterDeliveryProperties properties = properties(true);
        DeterministicSender sender = new DeterministicSender();
        for (int index = 0; index < NewsletterRetryPolicy.MAX_PROVIDER_ATTEMPTS; index++) {
            sender.enqueueFailure(new NewsletterEmailSendException(
                    NewsletterFailureType.TRANSIENT_PROVIDER,
                    true,
                    true));
        }
        NewsletterDeliveryServiceImpl deliveryService = deliveryService(
                properties, clock, sender);
        NewsletterCampaignServiceImpl campaignService = campaignService(properties, clock);

        deliveryService.processNextPendingDelivery();
        for (int attempt = 2; attempt <= NewsletterRetryPolicy.MAX_PROVIDER_ATTEMPTS; attempt++) {
            NewsletterDelivery failed = deliveryRepository.findById("delivery-1")
                    .orElseThrow();
            assertThat(failed.getNextRetryAt()).isNotNull();
            clock.set(failed.getNextRetryAt());
            deliveryService.processNextRetryDelivery();
        }
        deliveryService.processNextRetryDelivery();
        campaignService.completeNextCampaign();

        NewsletterDelivery failed = deliveryRepository.findById("delivery-1")
                .orElseThrow();
        assertThat(sender.calls()).isEqualTo(NewsletterRetryPolicy.MAX_PROVIDER_ATTEMPTS);
        assertThat(failed.getAttemptCount()).isEqualTo(NewsletterRetryPolicy.MAX_PROVIDER_ATTEMPTS);
        assertThat(failed.getStatus()).isEqualTo(NewsletterDeliveryStatus.FAILED);
        assertThat(failed.isRetryable()).isFalse();
        assertThat(failed.getNextRetryAt()).isNull();
        NewsletterCampaign completed = campaignRepository.findById("campaign-1")
                .orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(NewsletterCampaignStatus.FAILED);
        assertThat(completed.getFailedCount()).isEqualTo(1);
    }

    @Test
    void unknownProviderResultIsNeverAutomaticallyRetried() {
        blogPostRepository.insert(post(
                "post-1", "post-one", BlogPostStatus.PUBLISHED,
                NOW.minusSeconds(60), true));
        campaignRepository.insert(campaign("campaign-1", "post-1", 1L));
        subscriberRepository.insert(subscriber(
                "subscriber-1", NewsletterSubscriberStatus.ACTIVE));
        deliveryRepository.insert(delivery(
                "delivery-1", "campaign-1", "post-1", "subscriber-1",
                NewsletterDeliveryStatus.PENDING, 0, null, null, null));
        MutableUtcClock clock = new MutableUtcClock(NOW);
        NewsletterDeliveryProperties properties = properties(true);
        DeterministicSender sender = new DeterministicSender();
        sender.enqueueFailure(new NewsletterEmailSendException(
                NewsletterFailureType.TRANSIENT_PROVIDER,
                true,
                false));
        NewsletterDeliveryServiceImpl deliveryService = deliveryService(
                properties, clock, sender);

        deliveryService.processNextPendingDelivery();
        clock.advance(Duration.ofDays(1));
        deliveryService.processNextRetryDelivery();

        NewsletterDelivery failed = deliveryRepository.findById("delivery-1")
                .orElseThrow();
        assertThat(sender.calls()).isEqualTo(1);
        assertThat(failed.getLastErrorCode())
                .isEqualTo(NewsletterFailureType.PROVIDER_RESULT_UNKNOWN);
        assertThat(failed.isRetryable()).isFalse();
    }

    private NewsletterCampaignServiceImpl campaignService(
            NewsletterDeliveryProperties properties,
            MutableUtcClock clock) {
        return new NewsletterCampaignServiceImpl(
                blogPostRepository,
                campaignRepository,
                deliveryRepository,
                subscriberRepository,
                properties,
                clock,
                new NewsletterClaimTokenGenerator());
    }

    private NewsletterDeliveryServiceImpl deliveryService(
            NewsletterDeliveryProperties properties,
            MutableUtcClock clock,
            NewsletterEmailSender sender) {
        NewsletterUnsubscribeTokenProperties tokenProperties =
                new NewsletterUnsubscribeTokenProperties();
        tokenProperties.setSecret(
                "integration-newsletter-token-secret-at-least-32-bytes");
        tokenProperties.setKeyId("integration-active-v1");
        tokenProperties.afterPropertiesSet();
        return new NewsletterDeliveryServiceImpl(
                deliveryRepository,
                campaignRepository,
                subscriberRepository,
                blogPostRepository,
                sender,
                new NewsletterEmailNormalizer(
                        Validation.buildDefaultValidatorFactory().getValidator()),
                new NewsletterUnsubscribeTokenManager(
                        new ObjectMapper(), clock, tokenProperties),
                new NewsletterLinkBuilder(properties),
                new NewsletterEmailTemplateRenderer(properties),
                new NewsletterRetryPolicy(),
                properties,
                clock,
                new NewsletterClaimTokenGenerator());
    }

    private NewsletterDeliveryProperties properties(boolean sendingEnabled) {
        NewsletterDeliveryProperties properties = new NewsletterDeliveryProperties();
        properties.setSendingEnabled(sendingEnabled);
        properties.setFromEmail("newsletter@example.test");
        properties.setPublicBaseUrl("https://www.example.test");
        properties.setApiBaseUrl("https://api.example.test");
        properties.setBatchSize(100);
        properties.afterPropertiesSet();
        return properties;
    }

    private void race(Runnable first, Runnable second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = List.of(
                    executor.submit(() -> runAfterBarrier(first, ready, start)),
                    executor.submit(() -> runAfterBarrier(second, ready, start)));
            ready.await();
            start.countDown();
            futures.get(0).get();
            futures.get(1).get();
        } finally {
            executor.shutdownNow();
        }
    }

    private void runAfterBarrier(
            Runnable operation,
            CountDownLatch ready,
            CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트가 중단되었습니다.");
        }
        operation.run();
    }

    private BlogPost post(
            String id,
            String slug,
            BlogPostStatus status,
            Instant publishedAt,
            boolean newsletterEnabled) {
        return BlogPost.builder()
                .id(id)
                .slug(slug)
                .title("통합 테스트 " + id)
                .summary("요약")
                .contentMarkdown("본문")
                .authorName("토선생")
                .status(status)
                .relatedPostSlugs(List.of())
                .publishedAt(publishedAt)
                .newsletterEnabled(newsletterEnabled)
                .createdAt(NOW.minusSeconds(120))
                .updatedAt(NOW.minusSeconds(60))
                .build();
    }

    private NewsletterCampaign campaign(
            String id,
            String postId,
            Long totalRecipients) {
        return NewsletterCampaign.builder()
                .id(id)
                .postId(postId)
                .status(NewsletterCampaignStatus.SENDING)
                .scheduledAt(NOW.minusSeconds(60))
                .claimedAt(NOW.minusSeconds(30))
                .claimToken("campaign-claim-token")
                .claimExpiresAt(NOW.plusSeconds(300))
                .totalRecipients(totalRecipients)
                .createdAt(NOW.minusSeconds(60))
                .updatedAt(NOW.minusSeconds(30))
                .build();
    }

    private NewsletterDelivery delivery(
            String id,
            String campaignId,
            String postId,
            String subscriberId,
            NewsletterDeliveryStatus status,
            int attemptCount,
            String claimToken,
            Instant claimExpiresAt,
            Instant providerCallStartedAt) {
        return NewsletterDelivery.builder()
                .id(id)
                .campaignId(campaignId)
                .postId(postId)
                .subscriberId(subscriberId)
                .status(status)
                .attemptCount(attemptCount)
                .claimToken(claimToken)
                .claimExpiresAt(claimExpiresAt)
                .providerCallStartedAt(providerCallStartedAt)
                .retryable(false)
                .createdAt(NOW.minusSeconds(60))
                .updatedAt(NOW.minusSeconds(30))
                .build();
    }

    private NewsletterSubscriber subscriber(
            String id,
            NewsletterSubscriberStatus status) {
        return NewsletterSubscriber.builder()
                .id(id)
                .email(id + "@example.test")
                .status(status)
                .tokenVersion(1)
                .consentAt(NOW.minusSeconds(60))
                .subscribedAt(NOW.minusSeconds(60))
                .unsubscribedAt(status == NewsletterSubscriberStatus.UNSUBSCRIBED
                        ? NOW.minusSeconds(30)
                        : null)
                .createdAt(NOW.minusSeconds(60))
                .updatedAt(NOW.minusSeconds(30))
                .build();
    }

    private static final class DeterministicSender implements NewsletterEmailSender {

        private final Deque<Object> outcomes = new ArrayDeque<>();
        private int calls;

        void enqueueFailure(NewsletterEmailSendException exception) {
            outcomes.addLast(exception);
        }

        int calls() {
            return calls;
        }

        @Override
        public NewsletterEmailSendResult send(NewsletterEmailMessage message) {
            calls++;
            Object outcome = outcomes.pollFirst();
            if (outcome instanceof NewsletterEmailSendException exception) {
                throw exception;
            }
            return new NewsletterEmailSendResult("fake-message-" + calls);
        }
    }
}
