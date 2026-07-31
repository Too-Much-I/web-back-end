package web.tosunsaeng.integration.redis;

import com.fasterxml.jackson.databind.node.TextNode;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import web.tosunsaeng.domain.blog.domain.entity.BlogPost;
import web.tosunsaeng.domain.blog.domain.enums.BlogPostStatus;
import web.tosunsaeng.domain.blog.domain.repository.BlogPostRepository;
import web.tosunsaeng.domain.comment.application.AnonymousVisitorService;
import web.tosunsaeng.domain.comment.application.BlogCommentServiceImpl;
import web.tosunsaeng.domain.comment.application.CommentAbusePreventionService;
import web.tosunsaeng.domain.comment.application.CommentAbusePreventionServiceImpl;
import web.tosunsaeng.domain.comment.config.BlogCommentAbuseConfig;
import web.tosunsaeng.domain.comment.config.BlogCommentAbuseProperties;
import web.tosunsaeng.domain.comment.converter.BlogCommentConverter;
import web.tosunsaeng.domain.comment.domain.entity.AnonymousVisitor;
import web.tosunsaeng.domain.comment.domain.entity.BlogComment;
import web.tosunsaeng.domain.comment.domain.enums.CommentLimitScope;
import web.tosunsaeng.domain.comment.domain.policy.CommentRateLimitHasher;
import web.tosunsaeng.domain.comment.domain.policy.CommentRateLimitKeyFactory;
import web.tosunsaeng.domain.comment.domain.policy.CommentValidator;
import web.tosunsaeng.domain.comment.domain.policy.RedisFailureClassifier;
import web.tosunsaeng.domain.comment.domain.repository.BlogCommentRepository;
import web.tosunsaeng.domain.comment.domain.repository.CommentRateLimitRepository;
import web.tosunsaeng.domain.comment.domain.repository.RedisCommentRateLimitRepository;
import web.tosunsaeng.domain.comment.dto.BlogCommentRequestDTO;
import web.tosunsaeng.global.config.RedisConfig;
import web.tosunsaeng.integration.support.IntegrationContainers;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
class BlogCommentRedisIntegrationTest {

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
    void defaultWindowsAndAllFiveLimitsBlockWithoutPartialMutation() {
        BlogCommentAbuseProperties properties = defaultProperties();
        assertThat(properties.getVisitorShortWindowSeconds()).isEqualTo(10);
        assertThat(properties.getVisitorMediumWindowSeconds()).isEqualTo(600);
        assertThat(properties.getVisitorDailyWindowSeconds()).isEqualTo(86_400);
        assertThat(properties.getIpMediumWindowSeconds()).isEqualTo(600);
        assertThat(properties.getIpDailyWindowSeconds()).isEqualTo(86_400);
        RedisCommentRateLimitRepository repository = repository(properties);
        CommentRateLimitKeyFactory keyFactory = new CommentRateLimitKeyFactory();

        assertSingleBlocker(repository, keyFactory.create(
                "visitorShort", "ipShort", "post", "content"),
                0, properties.getVisitorShortLimit(), CommentLimitScope.VISITOR_SHORT);
        assertSingleBlocker(repository, keyFactory.create(
                "visitorMedium", "ipMediumA", "post", "content"),
                1, properties.getVisitorMediumLimit(), CommentLimitScope.VISITOR_MEDIUM);
        assertSingleBlocker(repository, keyFactory.create(
                "visitorDaily", "ipDailyA", "post", "content"),
                2, properties.getVisitorDailyLimit(), CommentLimitScope.VISITOR_DAILY);
        assertSingleBlocker(repository, keyFactory.create(
                "visitorIpMedium", "ipMedium", "post", "content"),
                3, properties.getIpMediumLimit(), CommentLimitScope.IP_MEDIUM);
        assertSingleBlocker(repository, keyFactory.create(
                "visitorIpDaily", "ipDaily", "post", "content"),
                4, properties.getIpDailyLimit(), CommentLimitScope.IP_DAILY);
    }

    @Test
    void duplicateOwnerCleanupAndExistingCounterTtlAreSafe() throws Exception {
        BlogCommentAbuseProperties properties = highLimitProperties();
        RedisCommentRateLimitRepository repository = repository(properties);
        CommentRateLimitKeyFactory.RateLimitKeys first = new CommentRateLimitKeyFactory()
                .create("sameVisitor", "sameIp", "post", "contentOne");
        CommentRateLimitKeyFactory.RateLimitKeys second =
                new CommentRateLimitKeyFactory.RateLimitKeys(
                        first.visitorShort(),
                        first.visitorMedium(),
                        first.visitorDaily(),
                        first.ipMedium(),
                        first.ipDaily(),
                        first.duplicate() + "Two");

        assertThat(repository.admit(first, "owner-one").allowed()).isTrue();
        Long ttlBefore = redisTemplate.getExpire(
                first.visitorShort(), TimeUnit.MILLISECONDS);
        Thread.sleep(600);
        assertThat(repository.admit(second, "owner-two").allowed()).isTrue();
        Long ttlAfter = redisTemplate.getExpire(
                first.visitorShort(), TimeUnit.MILLISECONDS);

        assertThat(ttlBefore).isNotNull().isPositive();
        assertThat(ttlAfter).isNotNull().isPositive().isLessThan(ttlBefore);
        assertThat(repository.releaseDuplicate(first.duplicate(), "wrong-owner"))
                .isFalse();
        assertThat(redisTemplate.hasKey(first.duplicate())).isTrue();
        assertThat(repository.releaseDuplicate(first.duplicate(), "owner-one"))
                .isTrue();
        assertThat(redisTemplate.hasKey(first.duplicate())).isFalse();
    }

    @Test
    void fixedWindowExpiresAndConcurrentRequestsNeverExceedLimit() throws Exception {
        BlogCommentAbuseProperties boundary = highLimitProperties();
        boundary.setVisitorShortLimit(1);
        boundary.setVisitorShortWindowSeconds(2);
        boundary.afterPropertiesSet();
        RedisCommentRateLimitRepository boundaryRepository = repository(boundary);
        CommentRateLimitKeyFactory.RateLimitKeys first = new CommentRateLimitKeyFactory()
                .create("boundaryVisitor", "boundaryIp", "post", "firstContent");
        CommentRateLimitKeyFactory.RateLimitKeys second =
                withDuplicate(first, first.duplicate() + "Second");
        assertThat(boundaryRepository.admit(first, "owner-first").allowed()).isTrue();
        assertThat(boundaryRepository.admit(second, "owner-second").allowed()).isFalse();
        Awaitility.await().atMost(Duration.ofSeconds(4))
                .until(() -> !Boolean.TRUE.equals(redisTemplate.hasKey(first.visitorShort())));
        assertThat(boundaryRepository.admit(second, "owner-second").allowed()).isTrue();

        flushAll();
        BlogCommentAbuseProperties concurrent = highLimitProperties();
        concurrent.setVisitorShortLimit(8);
        concurrent.afterPropertiesSet();
        RedisCommentRateLimitRepository concurrentRepository = repository(concurrent);
        CommentRateLimitKeyFactory.RateLimitKeys base = new CommentRateLimitKeyFactory()
                .create("concurrentVisitor", "concurrentIp", "post", "content");
        int allowed = concurrentAdmissions(concurrentRepository, base, 20);

        assertThat(allowed).isEqualTo(8);
        assertThat(redisTemplate.opsForValue().get(base.visitorShort()))
                .isEqualTo("8");
        assertThat(redisTemplate.opsForValue().get(base.visitorMedium()))
                .isEqualTo("8");
        assertThat(redisTemplate.opsForValue().get(base.ipMedium()))
                .isEqualTo("8");
    }

    @Test
    void mongoSaveFailureReleasesOnlyDuplicateReservationAndKeepsCounters() {
        BlogCommentAbuseProperties properties = highLimitProperties();
        CommentRateLimitHasher hasher = new CommentRateLimitHasher(properties);
        CommentRateLimitKeyFactory keyFactory = new CommentRateLimitKeyFactory();
        RedisCommentRateLimitRepository rateLimitRepository = repository(properties);
        CommentAbusePreventionService abuseService =
                new CommentAbusePreventionServiceImpl(
                        properties,
                        hasher,
                        keyFactory,
                        rateLimitRepository,
                        new RedisFailureClassifier(),
                        new SecureRandom());
        BlogPostRepository postRepository = mock(BlogPostRepository.class);
        BlogCommentRepository commentRepository = mock(BlogCommentRepository.class);
        AnonymousVisitorService visitorService = mock(AnonymousVisitorService.class);
        CommentValidator validator = mock(CommentValidator.class);
        AnonymousVisitor visitor = visitor();
        var prepared = new AnonymousVisitorService.PreparedVisitor(
                visitor, null, false);
        when(validator.validate(any(), eq(true))).thenReturn(
                new CommentValidator.ValidationResult("정상 댓글", List.of()));
        when(postRepository.findPublicPostBySlug(eq("post-one"), any()))
                .thenReturn(Optional.of(post()));
        when(visitorService.prepare(any(), any())).thenReturn(prepared);
        when(visitorService.commit(eq(prepared), any())).thenReturn(
                new AnonymousVisitorService.VisitorResolution(visitor, null));
        when(commentRepository.save(any(BlogComment.class)))
                .thenThrow(new IllegalStateException("simulated-mongo-failure"));
        BlogCommentServiceImpl service = new BlogCommentServiceImpl(
                postRepository,
                commentRepository,
                visitorService,
                abuseService,
                validator,
                mock(BlogCommentConverter.class),
                Clock.fixed(Instant.parse("2026-07-31T07:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.createComment(
                "post-one",
                new BlogCommentRequestDTO.CreateCommentRequest(
                        TextNode.valueOf("정상 댓글"), ""),
                "raw-token",
                () -> "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class);

        CommentRateLimitKeyFactory.RateLimitKeys keys = keyFactory.create(
                visitor.getTokenHash(),
                hasher.hashIp("127.0.0.1"),
                "post-1",
                hasher.hashContent("정상 댓글"));
        assertThat(redisTemplate.hasKey(keys.duplicate())).isFalse();
        assertThat(redisTemplate.opsForValue().get(keys.visitorShort()))
                .isEqualTo("1");
    }

    private void assertSingleBlocker(
            RedisCommentRateLimitRepository repository,
            CommentRateLimitKeyFactory.RateLimitKeys keys,
            int targetIndex,
            int limit,
            CommentLimitScope expectedScope) {
        flushAll();
        String targetKey = keys.asList().get(targetIndex);
        redisTemplate.opsForValue().set(
                targetKey, Integer.toString(limit), Duration.ofSeconds(60));

        CommentRateLimitRepository.AdmissionResult result = repository.admit(
                keys, "reservation-owner");

        assertThat(result.allowed()).isFalse();
        assertThat(result.blockers())
                .extracting(CommentRateLimitRepository.Blocker::scope)
                .containsExactly(expectedScope);
        assertThat(redisTemplate.opsForValue().get(targetKey))
                .isEqualTo(Integer.toString(limit));
        for (int index = 0; index < keys.asList().size(); index++) {
            if (index != targetIndex) {
                assertThat(redisTemplate.hasKey(keys.asList().get(index))).isFalse();
            }
        }
    }

    private int concurrentAdmissions(
            RedisCommentRateLimitRepository repository,
            CommentRateLimitKeyFactory.RateLimitKeys base,
            int requests) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < requests; index++) {
                int requestIndex = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return repository.admit(
                            withDuplicate(base, base.duplicate() + requestIndex),
                            "owner-" + requestIndex).allowed();
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

    private CommentRateLimitKeyFactory.RateLimitKeys withDuplicate(
            CommentRateLimitKeyFactory.RateLimitKeys base,
            String duplicate) {
        return new CommentRateLimitKeyFactory.RateLimitKeys(
                base.visitorShort(),
                base.visitorMedium(),
                base.visitorDaily(),
                base.ipMedium(),
                base.ipDaily(),
                duplicate);
    }

    private RedisCommentRateLimitRepository repository(
            BlogCommentAbuseProperties properties) {
        BlogCommentAbuseConfig config = new BlogCommentAbuseConfig();
        return new RedisCommentRateLimitRepository(
                redisTemplate,
                config.blogCommentAdmissionScript(),
                config.blogCommentDuplicateReleaseScript(),
                properties);
    }

    private BlogCommentAbuseProperties defaultProperties() {
        BlogCommentAbuseProperties properties = new BlogCommentAbuseProperties();
        properties.setSecret("integration-comment-rate-limit-secret-at-least-32-bytes");
        properties.afterPropertiesSet();
        return properties;
    }

    private BlogCommentAbuseProperties highLimitProperties() {
        BlogCommentAbuseProperties properties = defaultProperties();
        properties.setVisitorShortLimit(5);
        properties.setVisitorShortWindowSeconds(4);
        properties.setVisitorMediumLimit(20);
        properties.setVisitorMediumWindowSeconds(20);
        properties.setVisitorDailyLimit(40);
        properties.setVisitorDailyWindowSeconds(60);
        properties.setIpMediumLimit(30);
        properties.setIpMediumWindowSeconds(20);
        properties.setIpDailyLimit(60);
        properties.setIpDailyWindowSeconds(60);
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

    private BlogPost post() {
        Instant now = Instant.parse("2026-07-31T07:00:00Z");
        return BlogPost.builder()
                .id("post-1")
                .slug("post-one")
                .title("게시글")
                .summary("요약")
                .contentMarkdown("본문")
                .authorName("토선생")
                .status(BlogPostStatus.PUBLISHED)
                .relatedPostSlugs(List.of())
                .publishedAt(now.minusSeconds(60))
                .createdAt(now.minusSeconds(60))
                .updatedAt(now.minusSeconds(60))
                .build();
    }

    private AnonymousVisitor visitor() {
        Instant now = Instant.parse("2026-07-31T07:00:00Z");
        return AnonymousVisitor.builder()
                .id("visitor-1")
                .tokenHash("visitorTokenHash")
                .nickname("조용한 수달")
                .avatarSeed("avatar-seed")
                .avatarImageKey("character-image/avatar-v1.webp")
                .createdAt(now)
                .lastSeenAt(now)
                .build();
    }
}
