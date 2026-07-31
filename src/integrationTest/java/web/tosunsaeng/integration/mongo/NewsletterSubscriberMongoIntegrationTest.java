package web.tosunsaeng.integration.mongo;

import jakarta.validation.Validation;
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
import web.tosunsaeng.domain.newsletter.application.NewsletterServiceImpl;
import web.tosunsaeng.domain.newsletter.config.NewsletterMongoIndexInitializer;
import web.tosunsaeng.domain.newsletter.converter.NewsletterConverter;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterSubscriber;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterSubscriberStatus;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterEmailNormalizer;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterUnsubscribeTokenManager;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterSubscriberRepository;
import web.tosunsaeng.domain.newsletter.dto.NewsletterRequestDTO;
import web.tosunsaeng.domain.newsletter.dto.NewsletterResponseDTO;
import web.tosunsaeng.integration.support.IntegrationContainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DataMongoTest
@ActiveProfiles("test")
@Testcontainers
class NewsletterSubscriberMongoIntegrationTest {

    private static final String DATABASE = "phase08_subscriber";
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
    private NewsletterSubscriberRepository subscriberRepository;

    @BeforeEach
    void resetDatabase() {
        mongoTemplate.getDb().drop();
        new NewsletterMongoIndexInitializer(mongoTemplate).run(null);
    }

    @Test
    void createsEmailUniqueIndexAndRejectsDuplicateDocuments() {
        new NewsletterMongoIndexInitializer(mongoTemplate).run(null);

        List<Document> indexes = mongoTemplate.getCollection("newsletter_subscribers")
                .listIndexes()
                .into(new ArrayList<>());
        assertThat(indexes).anySatisfy(index -> {
            assertThat(index.getString("name"))
                    .isEqualTo("uk_newsletter_subscribers_email");
            assertThat(index.get("key", Document.class))
                    .isEqualTo(new Document("email", 1));
            assertThat(index.getBoolean("unique", false)).isTrue();
        });

        subscriberRepository.insert(subscriber(
                "subscriber-1", "same@example.test", NewsletterSubscriberStatus.ACTIVE, 1));
        assertThatThrownBy(() -> subscriberRepository.insert(subscriber(
                "subscriber-2", "same@example.test", NewsletterSubscriberStatus.ACTIVE, 1)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void concurrentNewSubscriptionRecoversDuplicateInsertAndKeepsOneDocument()
            throws Exception {
        NewsletterServiceImpl service = newsletterService();
        var request = new NewsletterRequestDTO.SubscribeRequest(
                " Concurrent@Example.Test ", true);

        List<NewsletterResponseDTO.StatusResult> results = race(
                () -> service.subscribe(request, "127.0.0.1"));

        assertThat(results)
                .extracting(NewsletterResponseDTO.StatusResult::getStatus)
                .containsExactlyInAnyOrder("ACTIVE", "ACTIVE");
        assertThat(subscriberRepository.findAll()).singleElement().satisfies(subscriber -> {
            assertThat(subscriber.getEmail()).isEqualTo("concurrent@example.test");
            assertThat(subscriber.getStatus()).isEqualTo(NewsletterSubscriberStatus.ACTIVE);
            assertThat(subscriber.getTokenVersion()).isEqualTo(1);
        });
    }

    @Test
    void concurrentReactivationIncrementsTokenVersionExactlyOnce() throws Exception {
        subscriberRepository.insert(subscriber(
                "subscriber-1",
                "reactivate@example.test",
                NewsletterSubscriberStatus.UNSUBSCRIBED,
                7));
        NewsletterServiceImpl service = newsletterService();
        var request = new NewsletterRequestDTO.SubscribeRequest(
                "reactivate@example.test", true);

        List<NewsletterResponseDTO.StatusResult> results = race(
                () -> service.subscribe(request, "127.0.0.1"));

        assertThat(results)
                .extracting(NewsletterResponseDTO.StatusResult::getStatus)
                .containsOnly("ACTIVE");
        NewsletterSubscriber stored = subscriberRepository.findById("subscriber-1")
                .orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(NewsletterSubscriberStatus.ACTIVE);
        assertThat(stored.getTokenVersion()).isEqualTo(8);
        assertThat(stored.getUnsubscribedAt()).isNull();
    }

    @Test
    void unsubscribeUsesStatusAndTokenVersionFencingForActiveAndBounced() {
        subscriberRepository.insert(subscriber(
                "active", "active@example.test", NewsletterSubscriberStatus.ACTIVE, 3));
        subscriberRepository.insert(subscriber(
                "bounced", "bounced@example.test", NewsletterSubscriberStatus.ACTIVE, 5));
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is("bounced")),
                new Update().set("status", NewsletterSubscriberStatus.BOUNCED),
                NewsletterSubscriber.class);

        assertThat(subscriberRepository.unsubscribeByIdAndVersion("active", 2, NOW))
                .isEmpty();
        assertThat(subscriberRepository.unsubscribeByIdAndVersion("active", 3, NOW))
                .isPresent();
        assertThat(subscriberRepository.unsubscribeByIdAndVersion("active", 3, NOW))
                .isEmpty();
        assertThat(subscriberRepository.unsubscribeByIdAndVersion("bounced", 5, NOW))
                .isPresent();
        assertThat(subscriberRepository.findById("active").orElseThrow().getStatus())
                .isEqualTo(NewsletterSubscriberStatus.UNSUBSCRIBED);
        assertThat(subscriberRepository.findById("bounced").orElseThrow().getStatus())
                .isEqualTo(NewsletterSubscriberStatus.UNSUBSCRIBED);
    }

    @Test
    void oldTokenVersionCannotUnsubscribeAfterReactivation() {
        subscriberRepository.insert(subscriber(
                "subscriber-1",
                "version@example.test",
                NewsletterSubscriberStatus.UNSUBSCRIBED,
                9));

        NewsletterSubscriber reactivated = subscriberRepository.reactivateByEmail(
                "version@example.test", NOW).orElseThrow();

        assertThat(reactivated.getTokenVersion()).isEqualTo(10);
        assertThat(subscriberRepository.unsubscribeByIdAndVersion(
                "subscriber-1", 9, NOW.plusSeconds(1))).isEmpty();
        assertThat(subscriberRepository.findById("subscriber-1").orElseThrow().getStatus())
                .isEqualTo(NewsletterSubscriberStatus.ACTIVE);
    }

    private NewsletterServiceImpl newsletterService() {
        return new NewsletterServiceImpl(
                subscriberRepository,
                clientIp -> {
                },
                new NewsletterEmailNormalizer(
                        Validation.buildDefaultValidatorFactory().getValidator()),
                mock(NewsletterUnsubscribeTokenManager.class),
                new NewsletterConverter(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private <T> List<T> race(Supplier<T> operation) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = List.of(
                    executor.submit(() -> runAfterBarrier(operation, ready, start)),
                    executor.submit(() -> runAfterBarrier(operation, ready, start)));
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

    private NewsletterSubscriber subscriber(
            String id,
            String email,
            NewsletterSubscriberStatus status,
            long tokenVersion) {
        return NewsletterSubscriber.builder()
                .id(id)
                .email(email)
                .status(status)
                .tokenVersion(tokenVersion)
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
