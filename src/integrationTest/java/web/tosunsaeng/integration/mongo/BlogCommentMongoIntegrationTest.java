package web.tosunsaeng.integration.mongo;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
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
import web.tosunsaeng.domain.comment.application.BlogCommentModerationServiceImpl;
import web.tosunsaeng.domain.comment.config.BlogCommentMongoIndexInitializer;
import web.tosunsaeng.domain.comment.converter.BlogCommentModerationConverter;
import web.tosunsaeng.domain.comment.domain.entity.AnonymousVisitor;
import web.tosunsaeng.domain.comment.domain.entity.BlogComment;
import web.tosunsaeng.domain.comment.domain.enums.CommentStatus;
import web.tosunsaeng.domain.comment.domain.enums.HiddenReason;
import web.tosunsaeng.domain.comment.domain.policy.AvatarImageUrlResolver;
import web.tosunsaeng.domain.comment.domain.repository.AnonymousVisitorRepository;
import web.tosunsaeng.domain.comment.domain.repository.BlogCommentRepository;
import web.tosunsaeng.domain.comment.dto.BlogCommentModerationDTO;
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

@DataMongoTest
@ActiveProfiles("test")
@Testcontainers
class BlogCommentMongoIntegrationTest {

    private static final String DATABASE = "phase08_comment";
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
    private AnonymousVisitorRepository anonymousVisitorRepository;

    @Autowired
    private BlogCommentRepository blogCommentRepository;

    @Autowired
    private BlogPostRepository blogPostRepository;

    @BeforeEach
    void resetDatabase() {
        mongoTemplate.getDb().drop();
        new BlogCommentMongoIndexInitializer(mongoTemplate).run(null);
        new BlogPostMongoIndexInitializer(mongoTemplate).run(null);
    }

    @Test
    void createsCommentIndexesIdempotentlyAndEnforcesVisitorTokenHashUnique() {
        new BlogCommentMongoIndexInitializer(mongoTemplate).run(null);

        List<Document> visitorIndexes = indexes("anonymous_visitors");
        List<Document> commentIndexes = indexes("blog_comments");
        assertThat(visitorIndexes).anySatisfy(index -> {
            assertThat(index.getString("name"))
                    .isEqualTo("uk_anonymous_visitors_token_hash");
            assertThat(index.getBoolean("unique", false)).isTrue();
        });
        assertThat(commentIndexes)
                .extracting(index -> index.getString("name"))
                .contains(
                        "idx_blog_comments_post_status_created_at",
                        "idx_blog_comments_anonymous_visitor_id",
                        "idx_blog_comments_status_created_at");

        anonymousVisitorRepository.insert(visitor("visitor-1", "same-token-hash"));
        assertThatThrownBy(() -> anonymousVisitorRepository.insert(
                visitor("visitor-2", "same-token-hash")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void visibleAndModerationQueriesUseStableSortAndHalfOpenPeriod() {
        Instant from = NOW.minusSeconds(60);
        Instant to = NOW;
        blogCommentRepository.saveAll(List.of(
                comment("at-from", "post-1", CommentStatus.VISIBLE, from),
                comment("inside", "post-1", CommentStatus.VISIBLE, from.plusSeconds(30)),
                comment("at-to", "post-1", CommentStatus.VISIBLE, to),
                comment("pending", "post-1", CommentStatus.PENDING, from.plusSeconds(40)),
                comment("hidden", "post-1", CommentStatus.HIDDEN, from.plusSeconds(50))));

        var visible = blogCommentRepository.findVisibleCommentsByPostId(
                "post-1", PageRequest.of(0, 20));
        var moderation = blogCommentRepository.findCommentsForModeration(
                null, "post-1", from, to, PageRequest.of(0, 20));

        assertThat(visible.getContent())
                .extracting(BlogComment::getId)
                .containsExactly("at-to", "inside", "at-from");
        assertThat(moderation.getContent())
                .extracting(BlogComment::getId)
                .containsExactly("hidden", "pending", "inside", "at-from");
    }

    @Test
    void hideAndRestoreAreAtomicAndOnlyOneConcurrentWorkerWins() throws Exception {
        blogCommentRepository.insert(comment(
                "visible", "post-1", CommentStatus.VISIBLE, NOW.minusSeconds(1)));

        List<Optional<BlogComment>> hidden = race(() -> blogCommentRepository.hideComment(
                "visible", HiddenReason.SPAM, NOW));

        assertThat(hidden.stream().filter(Optional::isPresent).count()).isEqualTo(1);
        BlogComment storedHidden = blogCommentRepository.findById("visible").orElseThrow();
        assertThat(storedHidden.getStatus()).isEqualTo(CommentStatus.HIDDEN);
        assertThat(storedHidden.getHiddenReason()).isEqualTo(HiddenReason.SPAM);
        assertThat(storedHidden.getHiddenAt()).isEqualTo(NOW);

        List<Optional<BlogComment>> restored = race(() -> blogCommentRepository.restoreComment(
                "visible", NOW.plusSeconds(1)));

        assertThat(restored.stream().filter(Optional::isPresent).count()).isEqualTo(1);
        BlogComment storedVisible = blogCommentRepository.findById("visible").orElseThrow();
        assertThat(storedVisible.getStatus()).isEqualTo(CommentStatus.VISIBLE);
        assertThat(storedVisible.getHiddenReason()).isNull();
        assertThat(storedVisible.getHiddenAt()).isNull();

        blogCommentRepository.insert(comment(
                "pending-comment", "post-1", CommentStatus.PENDING, NOW));
        assertThat(blogCommentRepository.hideComment(
                "pending-comment", HiddenReason.ABUSE, NOW.plusSeconds(2)))
                .isPresent();
    }

    @Test
    void moderationServiceAddsPostSlugsForMultipleCommentsUsingBatchResult() {
        blogPostRepository.saveAll(List.of(
                post("post-1", "first-post"),
                post("post-2", "second-post")));
        blogCommentRepository.saveAll(List.of(
                comment("comment-1", "post-1", CommentStatus.VISIBLE, NOW),
                comment("comment-2", "post-1", CommentStatus.VISIBLE, NOW.minusSeconds(1)),
                comment("comment-3", "post-2", CommentStatus.HIDDEN, NOW.minusSeconds(2))));
        var service = new BlogCommentModerationServiceImpl(
                blogCommentRepository,
                blogPostRepository,
                new BlogCommentModerationConverter(
                        new AvatarImageUrlResolver("https://to-teacher.com")),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.getComments(BlogCommentModerationDTO.CommentFilter.builder()
                .page(0)
                .size(20)
                .build());

        assertThat(result.getComments())
                .extracting(
                        BlogCommentModerationDTO.ModeratedCommentResult::getCommentId,
                        BlogCommentModerationDTO.ModeratedCommentResult::getPostSlug)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("comment-1", "first-post"),
                        org.assertj.core.groups.Tuple.tuple("comment-2", "first-post"),
                        org.assertj.core.groups.Tuple.tuple("comment-3", "second-post"));
    }

    private List<Optional<BlogComment>> race(
            Supplier<Optional<BlogComment>> operation) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Optional<BlogComment>>> futures = List.of(
                    executor.submit(() -> runAfterBarrier(operation, ready, start)),
                    executor.submit(() -> runAfterBarrier(operation, ready, start)));
            ready.await();
            start.countDown();
            return List.of(futures.get(0).get(), futures.get(1).get());
        } finally {
            executor.shutdownNow();
        }
    }

    private Optional<BlogComment> runAfterBarrier(
            Supplier<Optional<BlogComment>> operation,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return operation.get();
    }

    private List<Document> indexes(String collection) {
        return mongoTemplate.getCollection(collection)
                .listIndexes()
                .into(new ArrayList<>());
    }

    private AnonymousVisitor visitor(String id, String tokenHash) {
        return AnonymousVisitor.builder()
                .id(id)
                .tokenHash(tokenHash)
                .nickname("조용한 수달")
                .avatarSeed("seed-" + id)
                .avatarImageKey("character-image/avatar-v1.webp")
                .createdAt(NOW)
                .lastSeenAt(NOW)
                .build();
    }

    private BlogComment comment(
            String id,
            String postId,
            CommentStatus status,
            Instant createdAt) {
        return BlogComment.builder()
                .id(id)
                .postId(postId)
                .anonymousVisitorId("visitor-1")
                .nickname("조용한 수달")
                .avatarSeed("seed")
                .avatarImageKey("character-image/avatar-v1.webp")
                .content("통합 테스트 댓글")
                .status(status)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }

    private BlogPost post(String id, String slug) {
        return BlogPost.builder()
                .id(id)
                .slug(slug)
                .title(slug)
                .summary("요약")
                .contentMarkdown("본문")
                .authorName("토선생")
                .status(BlogPostStatus.PUBLISHED)
                .relatedPostSlugs(List.of())
                .publishedAt(NOW.minusSeconds(60))
                .createdAt(NOW.minusSeconds(60))
                .updatedAt(NOW.minusSeconds(60))
                .build();
    }
}
