package web.tosunsaeng.integration.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import web.tosunsaeng.domain.newsletter.application.NewsletterRateLimitServiceImpl;
import web.tosunsaeng.domain.newsletter.config.NewsletterConfig;
import web.tosunsaeng.domain.newsletter.config.NewsletterRateLimitProperties;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterRateLimitScope;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterRateLimitHasher;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterRateLimitKeyFactory;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterRedisFailureClassifier;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterRateLimitRepository;
import web.tosunsaeng.domain.newsletter.domain.repository.RedisNewsletterRateLimitRepository;
import web.tosunsaeng.domain.newsletter.exception.NewsletterRateLimitException;
import web.tosunsaeng.global.config.RedisConfig;
import web.tosunsaeng.integration.support.IntegrationContainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class NewsletterRedisIntegrationTest {

    private static final String CLIENT_IP = "192.0.2.10";

    @Container
    static final GenericContainer<?> REDIS = IntegrationContainers.redis();

    private LettuceConnectionFactory connectionFactory;
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUpRedis() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new RedisConfig().redisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        flushAll();
    }

    @AfterEach
    void closeRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void defaultLimitsAndWindowsUseOneAtomicTwoKeyAdmission() {
        NewsletterRateLimitProperties properties = defaultProperties();
        assertThat(properties.getMediumLimit()).isEqualTo(10);
        assertThat(properties.getMediumWindowSeconds()).isEqualTo(600);
        assertThat(properties.getDailyLimit()).isEqualTo(30);
        assertThat(properties.getDailyWindowSeconds()).isEqualTo(86_400);
        RedisNewsletterRateLimitRepository repository = repository(properties, redisTemplate);
        NewsletterRateLimitKeyFactory.RateLimitKeys keys = keys(properties, CLIENT_IP);

        assertThat(repository.admit(keys).allowed()).isTrue();

        assertThat(redisTemplate.opsForValue().get(keys.medium())).isEqualTo("1");
        assertThat(redisTemplate.opsForValue().get(keys.daily())).isEqualTo("1");
        assertThat(redisTemplate.getExpire(keys.medium(), TimeUnit.SECONDS))
                .isBetween(1L, properties.getMediumWindowSeconds());
        assertThat(redisTemplate.getExpire(keys.daily(), TimeUnit.SECONDS))
                .isBetween(1L, properties.getDailyWindowSeconds());
    }

    @Test
    void blockedRequestDoesNotMutateEitherCounter() {
        NewsletterRateLimitProperties properties = defaultProperties();
        RedisNewsletterRateLimitRepository repository = repository(properties, redisTemplate);
        NewsletterRateLimitKeyFactory.RateLimitKeys keys = keys(properties, CLIENT_IP);
        redisTemplate.opsForValue().set(
                keys.medium(),
                Integer.toString(properties.getMediumLimit()),
                Duration.ofSeconds(90));

        NewsletterRateLimitRepository.AdmissionResult mediumBlocked = repository.admit(keys);

        assertThat(mediumBlocked.allowed()).isFalse();
        assertThat(mediumBlocked.blockers())
                .extracting(NewsletterRateLimitRepository.Blocker::scope)
                .containsExactly(NewsletterRateLimitScope.IP_MEDIUM);
        assertThat(redisTemplate.opsForValue().get(keys.medium())).isEqualTo("10");
        assertThat(redisTemplate.hasKey(keys.daily())).isFalse();

        flushAll();
        redisTemplate.opsForValue().set(
                keys.daily(),
                Integer.toString(properties.getDailyLimit()),
                Duration.ofSeconds(120));

        NewsletterRateLimitRepository.AdmissionResult dailyBlocked = repository.admit(keys);

        assertThat(dailyBlocked.allowed()).isFalse();
        assertThat(dailyBlocked.blockers())
                .extracting(NewsletterRateLimitRepository.Blocker::scope)
                .containsExactly(NewsletterRateLimitScope.IP_DAILY);
        assertThat(redisTemplate.hasKey(keys.medium())).isFalse();
        assertThat(redisTemplate.opsForValue().get(keys.daily())).isEqualTo("30");
    }

    @Test
    void retryAfterUsesLongestBlockerTtlAndExistingTtlIsNotExtended() throws Exception {
        NewsletterRateLimitProperties properties = shortWindowProperties();
        RedisNewsletterRateLimitRepository repository = repository(properties, redisTemplate);
        NewsletterRateLimitKeyFactory.RateLimitKeys keys = keys(properties, CLIENT_IP);

        assertThat(repository.admit(keys).allowed()).isTrue();
        Long mediumTtlBefore = redisTemplate.getExpire(keys.medium(), TimeUnit.MILLISECONDS);
        Long dailyTtlBefore = redisTemplate.getExpire(keys.daily(), TimeUnit.MILLISECONDS);
        Thread.sleep(600);
        assertThat(repository.admit(keys).allowed()).isTrue();
        Long mediumTtlAfter = redisTemplate.getExpire(keys.medium(), TimeUnit.MILLISECONDS);
        Long dailyTtlAfter = redisTemplate.getExpire(keys.daily(), TimeUnit.MILLISECONDS);

        assertThat(mediumTtlAfter).isPositive().isLessThan(mediumTtlBefore);
        assertThat(dailyTtlAfter).isPositive().isLessThan(dailyTtlBefore);

        redisTemplate.opsForValue().set(
                keys.medium(),
                Integer.toString(properties.getMediumLimit()),
                Duration.ofSeconds(12));
        redisTemplate.opsForValue().set(
                keys.daily(),
                Integer.toString(properties.getDailyLimit()),
                Duration.ofSeconds(27));
        NewsletterRateLimitServiceImpl service = service(properties, repository);

        assertThatThrownBy(() -> service.check(CLIENT_IP))
                .isInstanceOfSatisfying(NewsletterRateLimitException.class, exception -> {
                    assertThat(exception.getLimitScope())
                            .isEqualTo(NewsletterRateLimitScope.IP_DAILY);
                    assertThat(exception.getRetryAfterSeconds()).isBetween(24L, 27L);
                });
        assertThat(redisTemplate.opsForValue().get(keys.medium()))
                .isEqualTo(Integer.toString(properties.getMediumLimit()));
        assertThat(redisTemplate.opsForValue().get(keys.daily()))
                .isEqualTo(Integer.toString(properties.getDailyLimit()));
    }

    @Test
    void concurrentRequestsNeverExceedMediumLimit() throws Exception {
        NewsletterRateLimitProperties properties = shortWindowProperties();
        properties.setMediumLimit(8);
        properties.setDailyLimit(30);
        properties.afterPropertiesSet();
        RedisNewsletterRateLimitRepository repository = repository(properties, redisTemplate);
        NewsletterRateLimitKeyFactory.RateLimitKeys keys = keys(properties, CLIENT_IP);

        int allowed = concurrentAdmissions(repository, keys, 20);

        assertThat(allowed).isEqualTo(8);
        assertThat(redisTemplate.opsForValue().get(keys.medium())).isEqualTo("8");
        assertThat(redisTemplate.opsForValue().get(keys.daily())).isEqualTo("8");
    }

    @Test
    void realConnectionFailureUsesFailOpenContract() {
        NewsletterRateLimitProperties properties = defaultProperties();
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(200))
                .shutdownTimeout(Duration.ZERO)
                .build();
        RedisStandaloneConfiguration unavailable =
                new RedisStandaloneConfiguration("127.0.0.1", 1);
        LettuceConnectionFactory unavailableFactory =
                new LettuceConnectionFactory(unavailable, clientConfiguration);
        unavailableFactory.afterPropertiesSet();
        unavailableFactory.start();
        RedisTemplate<String, Object> unavailableTemplate =
                new RedisConfig().redisTemplate(unavailableFactory);
        unavailableTemplate.afterPropertiesSet();
        NewsletterRateLimitServiceImpl service = service(
                properties,
                repository(properties, unavailableTemplate));

        try {
            assertThatCode(() -> service.check(CLIENT_IP)).doesNotThrowAnyException();
        } finally {
            unavailableFactory.destroy();
        }
    }

    private int concurrentAdmissions(
            RedisNewsletterRateLimitRepository repository,
            NewsletterRateLimitKeyFactory.RateLimitKeys keys,
            int requests) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < requests; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return repository.admit(keys).allowed();
                }));
            }
            ready.await();
            start.countDown();
            int allowed = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    allowed++;
                }
            }
            return allowed;
        } finally {
            executor.shutdownNow();
        }
    }

    private NewsletterRateLimitServiceImpl service(
            NewsletterRateLimitProperties properties,
            NewsletterRateLimitRepository repository) {
        return new NewsletterRateLimitServiceImpl(
                properties,
                new NewsletterRateLimitHasher(properties),
                new NewsletterRateLimitKeyFactory(),
                repository,
                new NewsletterRedisFailureClassifier());
    }

    private RedisNewsletterRateLimitRepository repository(
            NewsletterRateLimitProperties properties,
            RedisTemplate<String, Object> template) {
        return new RedisNewsletterRateLimitRepository(
                template,
                new NewsletterConfig().newsletterSubscribeRateLimitScript(),
                properties);
    }

    private NewsletterRateLimitKeyFactory.RateLimitKeys keys(
            NewsletterRateLimitProperties properties,
            String clientIp) {
        String ipHash = new NewsletterRateLimitHasher(properties).hashIp(clientIp);
        return new NewsletterRateLimitKeyFactory().create(ipHash);
    }

    private NewsletterRateLimitProperties defaultProperties() {
        NewsletterRateLimitProperties properties = new NewsletterRateLimitProperties();
        properties.setSecret(
                "integration-newsletter-rate-limit-secret-at-least-32-bytes");
        properties.afterPropertiesSet();
        return properties;
    }

    private NewsletterRateLimitProperties shortWindowProperties() {
        NewsletterRateLimitProperties properties = defaultProperties();
        properties.setMediumLimit(10);
        properties.setMediumWindowSeconds(20);
        properties.setDailyLimit(30);
        properties.setDailyWindowSeconds(60);
        properties.afterPropertiesSet();
        return properties;
    }

    private void flushAll() {
        RedisConnection connection = connectionFactory.getConnection();
        try {
            connection.serverCommands().flushAll();
        } finally {
            connection.close();
        }
    }
}
