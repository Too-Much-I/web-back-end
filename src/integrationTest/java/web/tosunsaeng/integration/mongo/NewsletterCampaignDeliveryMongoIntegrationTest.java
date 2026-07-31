package web.tosunsaeng.integration.mongo;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import web.tosunsaeng.domain.blog.domain.repository.BlogPostRepository;
import web.tosunsaeng.domain.newsletter.application.NewsletterCampaignService;
import web.tosunsaeng.domain.newsletter.application.NewsletterCampaignServiceImpl;
import web.tosunsaeng.domain.newsletter.application.NewsletterOperationsService;
import web.tosunsaeng.domain.newsletter.application.NewsletterOperationsServiceImpl;
import web.tosunsaeng.domain.newsletter.config.NewsletterDeliveryProperties;
import web.tosunsaeng.domain.newsletter.config.NewsletterMongoIndexInitializer;
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
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterCampaignRepository;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterDeliveryRepository;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterSubscriberRepository;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailSender;
import web.tosunsaeng.integration.support.IntegrationContainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataMongoTest
@ActiveProfiles("test")
@Testcontainers
class NewsletterCampaignDeliveryMongoIntegrationTest {

    private static final String DATABASE = "phase08_campaign_delivery";
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
        new NewsletterMongoIndexInitializer(mongoTemplate).run(null);
    }

    @Test
    void createsCampaignAndDeliveryIndexesAndEnforcesBothUniqueContracts() {
        new NewsletterMongoIndexInitializer(mongoTemplate).run(null);

        List<Document> campaignIndexes = indexes("newsletter_campaigns");
        List<Document> deliveryIndexes = indexes("newsletter_deliveries");
        assertThat(campaignIndexes).anySatisfy(index -> {
            assertThat(index.getString("name"))
                    .isEqualTo("uk_newsletter_campaigns_post_id");
            assertThat(index.getBoolean("unique", false)).isTrue();
        });
        assertThat(deliveryIndexes).anySatisfy(index -> {
            assertThat(index.getString("name"))
                    .isEqualTo("uk_newsletter_deliveries_post_subscriber");
            assertThat(index.get("key", Document.class)).isEqualTo(
                    new Document("postId", 1).append("subscriberId", 1));
            assertThat(index.getBoolean("unique", false)).isTrue();
        });

        campaignRepository.insert(campaign(
                "campaign-1", "post-1", NewsletterCampaignStatus.SCHEDULED, null, null));
        assertThatThrownBy(() -> campaignRepository.insert(campaign(
                "campaign-2", "post-1", NewsletterCampaignStatus.SCHEDULED, null, null)))
                .isInstanceOf(DuplicateKeyException.class);

        deliveryRepository.insert(delivery(
                "delivery-1", "campaign-1", "post-1", "subscriber-1",
                NewsletterDeliveryStatus.PENDING, 0, false,
                null, null, null, null, null));
        assertThatThrownBy(() -> deliveryRepository.insert(delivery(
                "delivery-2", "campaign-2", "post-1", "subscriber-1",
                NewsletterDeliveryStatus.PENDING, 0, false,
                null, null, null, null, null)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void concurrentClaimsHaveOneWinnerAndOldTokensCannotMutateState() throws Exception {
        campaignRepository.insert(campaign(
                "campaign-1", "post-1", NewsletterCampaignStatus.SCHEDULED, null, null));

        List<Optional<NewsletterCampaign>> campaignClaims = race(
                () -> campaignRepository.claimNextScheduled(
                        NOW, NOW.plusSeconds(300), "campaign-token-a"),
                () -> campaignRepository.claimNextScheduled(
                        NOW, NOW.plusSeconds(300), "campaign-token-b"));

        assertThat(campaignClaims.stream().filter(Optional::isPresent).count()).isEqualTo(1);
        NewsletterCampaign claimedCampaign = campaignRepository.findById("campaign-1")
                .orElseThrow();
        assertThat(claimedCampaign.getStatus()).isEqualTo(NewsletterCampaignStatus.SENDING);
        assertThat(campaignRepository.extendGenerationClaim(
                "campaign-1", "wrong-token", NOW.plusSeconds(600), NOW)).isFalse();
        assertThat(campaignRepository.extendGenerationClaim(
                "campaign-1", claimedCampaign.getClaimToken(), NOW.plusSeconds(600), NOW))
                .isTrue();

        deliveryRepository.insert(delivery(
                "delivery-1", "campaign-1", "post-1", "subscriber-1",
                NewsletterDeliveryStatus.PENDING, 0, false,
                null, null, null, null, null));
        List<Optional<NewsletterDelivery>> deliveryClaims = race(
                () -> deliveryRepository.claimNextPending(
                        NOW, NOW.plusSeconds(300), "delivery-token-a"),
                () -> deliveryRepository.claimNextPending(
                        NOW, NOW.plusSeconds(300), "delivery-token-b"));

        assertThat(deliveryClaims.stream().filter(Optional::isPresent).count()).isEqualTo(1);
        NewsletterDelivery claimedDelivery = deliveryRepository.findById("delivery-1")
                .orElseThrow();
        assertThat(deliveryRepository.markProviderCallStarted(
                "delivery-1", "wrong-token", NOW, NOW.plusSeconds(300))).isEmpty();
        NewsletterDelivery started = deliveryRepository.markProviderCallStarted(
                "delivery-1",
                claimedDelivery.getClaimToken(),
                NOW,
                NOW.plusSeconds(300)).orElseThrow();
        assertThat(started.getAttemptCount()).isEqualTo(1);
        assertThat(deliveryRepository.markSent(
                "delivery-1", "wrong-token", "provider-id", NOW)).isFalse();
        assertThat(deliveryRepository.markSent(
                "delivery-1", claimedDelivery.getClaimToken(), "provider-id", NOW))
                .isTrue();
    }

    @Test
    void staleRecoveryAndRetryQueryRespectProviderBoundaryAndAttemptLimit() {
        deliveryRepository.saveAll(List.of(
                delivery(
                        "before-provider", "campaign-1", "post-1", "subscriber-1",
                        NewsletterDeliveryStatus.SENDING, 0, false,
                        "claim-before", NOW.minusSeconds(1), null, null, null),
                delivery(
                        "after-provider", "campaign-1", "post-1", "subscriber-2",
                        NewsletterDeliveryStatus.SENDING, 1, false,
                        "claim-after", NOW.minusSeconds(1), NOW.minusSeconds(30), null, null),
                delivery(
                        "retry-due", "campaign-1", "post-1", "subscriber-3",
                        NewsletterDeliveryStatus.FAILED, 2, true,
                        null, null, null, NOW, NewsletterFailureType.TRANSIENT_PROVIDER),
                delivery(
                        "retry-max", "campaign-1", "post-1", "subscriber-4",
                        NewsletterDeliveryStatus.FAILED, NewsletterRetryPolicy.MAX_PROVIDER_ATTEMPTS,
                        true, null, null, null, NOW, NewsletterFailureType.TRANSIENT_PROVIDER)));

        assertThat(deliveryRepository.recoverNextStaleBeforeProvider(NOW))
                .map(NewsletterDelivery::getId)
                .contains("before-provider");
        assertThat(deliveryRepository.failNextStaleAfterProvider(NOW))
                .map(NewsletterDelivery::getId)
                .contains("after-provider");
        NewsletterDelivery unknown = deliveryRepository.findById("after-provider")
                .orElseThrow();
        assertThat(unknown.getStatus()).isEqualTo(NewsletterDeliveryStatus.FAILED);
        assertThat(unknown.getLastErrorCode())
                .isEqualTo(NewsletterFailureType.PROVIDER_RESULT_UNKNOWN);
        assertThat(unknown.isRetryable()).isFalse();

        assertThat(deliveryRepository.claimNextRetry(
                NOW, NOW.plusSeconds(300), "retry-token", NewsletterRetryPolicy.MAX_PROVIDER_ATTEMPTS))
                .map(NewsletterDelivery::getId)
                .contains("retry-due");
        assertThat(deliveryRepository.findById("retry-max").orElseThrow().getStatus())
                .isEqualTo(NewsletterDeliveryStatus.FAILED);
    }

    @Test
    void campaignCompletionWaitsForRetryableFailuresAndStoresFinalCounts() {
        campaignRepository.insert(campaign(
                "campaign-1", "post-1", NewsletterCampaignStatus.SENDING,
                "claim-token", 4L));
        deliveryRepository.saveAll(List.of(
                delivery("sent", "campaign-1", "post-1", "subscriber-1",
                        NewsletterDeliveryStatus.SENT, 1, false,
                        null, null, NOW, null, null),
                delivery("skipped", "campaign-1", "post-1", "subscriber-2",
                        NewsletterDeliveryStatus.SKIPPED, 0, false,
                        null, null, null, null, null),
                delivery("final-failed", "campaign-1", "post-1", "subscriber-3",
                        NewsletterDeliveryStatus.FAILED, 1, false,
                        null, null, null, null, NewsletterFailureType.PERMANENT_REQUEST),
                delivery("retryable", "campaign-1", "post-1", "subscriber-4",
                        NewsletterDeliveryStatus.FAILED, 1, true,
                        null, null, null, NOW, NewsletterFailureType.TRANSIENT_PROVIDER)));
        NewsletterCampaignServiceImpl service = campaignService(true);

        service.completeNextCampaign();
        assertThat(campaignRepository.findById("campaign-1").orElseThrow().getStatus())
                .isEqualTo(NewsletterCampaignStatus.SENDING);

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is("retryable")),
                new Update().set("retryable", false),
                NewsletterDelivery.class);
        service.completeNextCampaign();

        NewsletterCampaign completed = campaignRepository.findById("campaign-1")
                .orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(NewsletterCampaignStatus.FAILED);
        assertThat(completed.getTotalRecipients()).isEqualTo(4);
        assertThat(completed.getSentCount()).isEqualTo(1);
        assertThat(completed.getFailedCount()).isEqualTo(2);
        assertThat(completed.getSkippedCount()).isEqualTo(1);
        assertThat(completed.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    void manualRetryUsesExistingDocumentsInBatchesOfOneHundred() {
        campaignRepository.insert(campaign(
                "campaign-1", "post-1", NewsletterCampaignStatus.FAILED, null, 101L));
        List<NewsletterSubscriber> subscribers = new ArrayList<>();
        List<NewsletterDelivery> deliveries = new ArrayList<>();
        for (int index = 0; index < 205; index++) {
            subscribers.add(subscriber(
                    "subscriber-%03d".formatted(index),
                    index == 0
                            ? NewsletterSubscriberStatus.UNSUBSCRIBED
                            : NewsletterSubscriberStatus.ACTIVE));
        }
        for (int index = 0; index < 101; index++) {
            deliveries.add(delivery(
                    "delivery-%03d".formatted(index),
                    "campaign-1",
                    "post-1",
                    "subscriber-%03d".formatted(index),
                    NewsletterDeliveryStatus.FAILED,
                    1,
                    true,
                    null,
                    null,
                    null,
                    NOW,
                    NewsletterFailureType.TRANSIENT_PROVIDER));
        }
        deliveries.add(delivery(
                "excluded-unknown", "campaign-1", "post-1", "subscriber-150",
                NewsletterDeliveryStatus.FAILED, 1, true,
                null, null, null, NOW, NewsletterFailureType.PROVIDER_RESULT_UNKNOWN));
        deliveries.add(delivery(
                "excluded-max", "campaign-1", "post-1", "subscriber-151",
                NewsletterDeliveryStatus.FAILED, NewsletterRetryPolicy.MAX_PROVIDER_ATTEMPTS, true,
                null, null, null, NOW, NewsletterFailureType.TRANSIENT_PROVIDER));
        deliveries.add(delivery(
                "excluded-nonretry", "campaign-1", "post-1", "subscriber-152",
                NewsletterDeliveryStatus.FAILED, 1, false,
                null, null, null, NOW, NewsletterFailureType.PERMANENT_REQUEST));
        deliveries.add(delivery(
                "excluded-sent", "campaign-1", "post-1", "subscriber-153",
                NewsletterDeliveryStatus.SENT, 1, false,
                null, null, NOW, null, null));
        subscriberRepository.saveAll(subscribers);
        deliveryRepository.saveAll(deliveries);

        assertThat(subscriberRepository.findActiveAfterId(null, 100)).hasSize(100);
        String cursor = subscriberRepository.findActiveAfterId(null, 100).getLast().getId();
        assertThat(subscriberRepository.findActiveAfterId(cursor, 100)).hasSize(100);
        String secondCursor = subscriberRepository.findActiveAfterId(cursor, 100)
                .getLast().getId();
        assertThat(subscriberRepository.findActiveAfterId(secondCursor, 100)).hasSize(4);

        NewsletterCampaignService campaignService = mock(NewsletterCampaignService.class);
        when(campaignService.reopenFailedCampaign("campaign-1")).thenReturn(true);
        NewsletterEmailSender sender = mock(NewsletterEmailSender.class);
        NewsletterOperationsServiceImpl operations = operationsService(campaignService, sender);
        long documentCount = deliveryRepository.count();

        NewsletterOperationsService.ManualRetryBatchResult first =
                operations.retryFailedDeliveriesByPostId("post-1");
        NewsletterOperationsService.ManualRetryBatchResult second =
                operations.retryFailedDeliveriesByPostId("post-1");

        assertThat(first.retriedCount()).isEqualTo(99);
        assertThat(first.skippedCount()).isEqualTo(1);
        assertThat(first.hasMore()).isTrue();
        assertThat(second.retriedCount()).isEqualTo(1);
        assertThat(second.skippedCount()).isZero();
        assertThat(second.hasMore()).isFalse();
        assertThat(deliveryRepository.count()).isEqualTo(documentCount);
        assertThat(deliveryRepository.findById("delivery-000").orElseThrow().getStatus())
                .isEqualTo(NewsletterDeliveryStatus.SKIPPED);
        assertThat(deliveryRepository.findById("delivery-100").orElseThrow().getStatus())
                .isEqualTo(NewsletterDeliveryStatus.PENDING);
        assertThat(deliveryRepository.findById("excluded-unknown").orElseThrow().getStatus())
                .isEqualTo(NewsletterDeliveryStatus.FAILED);
        verify(sender, never()).send(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void concurrentCancellationHasOnlyOneWinner() throws Exception {
        campaignRepository.insert(campaign(
                "campaign-1", "post-1", NewsletterCampaignStatus.SCHEDULED, null, null));

        List<Boolean> results = race(
                () -> campaignRepository.cancelScheduled("campaign-1", NOW),
                () -> campaignRepository.cancelScheduled("campaign-1", NOW));

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(campaignRepository.findById("campaign-1").orElseThrow().getStatus())
                .isEqualTo(NewsletterCampaignStatus.CANCELED);
    }

    private NewsletterCampaignServiceImpl campaignService(boolean sendingEnabled) {
        NewsletterDeliveryProperties properties = properties(sendingEnabled);
        return new NewsletterCampaignServiceImpl(
                blogPostRepository,
                campaignRepository,
                deliveryRepository,
                subscriberRepository,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new NewsletterClaimTokenGenerator());
    }

    private NewsletterOperationsServiceImpl operationsService(
            NewsletterCampaignService campaignService,
            NewsletterEmailSender sender) {
        return new NewsletterOperationsServiceImpl(
                mock(BlogPostRepository.class),
                campaignRepository,
                deliveryRepository,
                subscriberRepository,
                campaignService,
                sender,
                mock(NewsletterEmailNormalizer.class),
                mock(NewsletterLinkBuilder.class),
                mock(NewsletterEmailTemplateRenderer.class),
                properties(true),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private NewsletterDeliveryProperties properties(boolean sendingEnabled) {
        NewsletterDeliveryProperties properties = new NewsletterDeliveryProperties();
        properties.setSendingEnabled(sendingEnabled);
        properties.setFromEmail("newsletter@example.test");
        properties.setPublicBaseUrl("https://www.example.test");
        properties.setApiBaseUrl("https://api.example.test");
        properties.afterPropertiesSet();
        return properties;
    }

    private List<Document> indexes(String collection) {
        return mongoTemplate.getCollection(collection)
                .listIndexes()
                .into(new ArrayList<>());
    }

    private <T> List<T> race(Supplier<T> first, Supplier<T> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = List.of(
                    executor.submit(() -> runAfterBarrier(first, ready, start)),
                    executor.submit(() -> runAfterBarrier(second, ready, start)));
            ready.await();
            start.countDown();
            return List.of(futures.get(0).get(), futures.get(1).get());
        } finally {
            executor.shutdownNow();
        }
    }

    private <T> T runAfterBarrier(
            Supplier<T> operation,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return operation.get();
    }

    private NewsletterCampaign campaign(
            String id,
            String postId,
            NewsletterCampaignStatus status,
            String claimToken,
            Long totalRecipients) {
        return NewsletterCampaign.builder()
                .id(id)
                .postId(postId)
                .status(status)
                .scheduledAt(NOW.minusSeconds(60))
                .claimedAt(status == NewsletterCampaignStatus.SENDING ? NOW.minusSeconds(30) : null)
                .claimToken(claimToken)
                .claimExpiresAt(status == NewsletterCampaignStatus.SENDING
                        ? NOW.plusSeconds(300)
                        : null)
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
            boolean retryable,
            String claimToken,
            Instant claimExpiresAt,
            Instant providerCallStartedAt,
            Instant nextRetryAt,
            NewsletterFailureType error) {
        return NewsletterDelivery.builder()
                .id(id)
                .campaignId(campaignId)
                .postId(postId)
                .subscriberId(subscriberId)
                .status(status)
                .attemptCount(attemptCount)
                .retryable(retryable)
                .claimToken(claimToken)
                .claimExpiresAt(claimExpiresAt)
                .providerCallStartedAt(providerCallStartedAt)
                .nextRetryAt(nextRetryAt)
                .lastErrorCode(error)
                .sentAt(status == NewsletterDeliveryStatus.SENT ? NOW : null)
                .failedAt(status == NewsletterDeliveryStatus.FAILED ? NOW : null)
                .skippedAt(status == NewsletterDeliveryStatus.SKIPPED ? NOW : null)
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
}
