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
import web.tosunsaeng.integration.support.IntegrationContainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataMongoTest
@ActiveProfiles("test")
@Testcontainers
class BlogPostMongoIntegrationTest {

    private static final String DATABASE = "phase08_blog";
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

    @BeforeEach
    void resetDatabase() {
        mongoTemplate.getDb().drop();
        ensureIndexes();
    }

    @Test
    void createsExactIndexesIdempotentlyAndEnforcesUniqueSlug() {
        ensureIndexes();

        List<Document> indexes = indexes("blog_posts");
        assertThat(indexes).anySatisfy(index -> {
            assertThat(index.getString("name")).isEqualTo("uk_blog_posts_slug");
            assertThat(index.get("key", Document.class))
                    .isEqualTo(new Document("slug", 1));
            assertThat(index.getBoolean("unique", false)).isTrue();
        });
        assertThat(indexes).anySatisfy(index -> {
            assertThat(index.getString("name"))
                    .isEqualTo("idx_blog_posts_status_published_at");
            assertThat(index.get("key", Document.class))
                    .isEqualTo(new Document("status", 1).append("publishedAt", -1));
        });

        blogPostRepository.insert(post(
                "post-1", "same-slug", "첫 글", BlogPostStatus.PUBLISHED,
                NOW, NOW.minusSeconds(20), true));
        assertThatThrownBy(() -> blogPostRepository.insert(post(
                "post-2", "same-slug", "둘째 글", BlogPostStatus.PUBLISHED,
                NOW, NOW.minusSeconds(10), true)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void publicQueriesUseBoundaryStableSortAndExcludeNonPublicPosts() {
        blogPostRepository.saveAll(List.of(
                post("past", "past-post", "과거 글", BlogPostStatus.PUBLISHED,
                        NOW.minusSeconds(60), NOW.minusSeconds(60), false),
                post("sort-a", "sort-a", "정렬 A", BlogPostStatus.PUBLISHED,
                        NOW.minusSeconds(30), NOW.minusSeconds(20), false),
                post("sort-b", "sort-b", "정렬 B", BlogPostStatus.PUBLISHED,
                        NOW.minusSeconds(30), NOW.minusSeconds(20), false),
                post("boundary", "boundary-post", "경계 글", BlogPostStatus.PUBLISHED,
                        NOW, NOW, false),
                post("future", "future-post", "미래 글", BlogPostStatus.PUBLISHED,
                        NOW.plusSeconds(1), NOW, false),
                post("draft", "draft-post", "초안", BlogPostStatus.DRAFT,
                        NOW.minusSeconds(1), NOW, false),
                post("archived", "archived-post", "보관", BlogPostStatus.ARCHIVED,
                        NOW.minusSeconds(1), NOW, false),
                post("null-date", "null-date", "날짜 없음", BlogPostStatus.PUBLISHED,
                        null, NOW, false)));

        var page = blogPostRepository.findPublicPosts(NOW, PageRequest.of(0, 20));

        assertThat(page.getContent())
                .extracting(BlogPost::getId)
                .containsExactly("boundary", "sort-b", "sort-a", "past");
        assertThat(blogPostRepository.findPublicPostBySlug("boundary-post", NOW))
                .isPresent();
        assertThat(blogPostRepository.findPublicPostBySlug("future-post", NOW))
                .isEmpty();
    }

    @Test
    void titleSearchEscapesRegexAndRelatedSlugQueryIsBatched() {
        blogPostRepository.saveAll(List.of(
                post("literal", "literal-post", "정규식 .* 문자", BlogPostStatus.PUBLISHED,
                        NOW.minusSeconds(10), NOW.minusSeconds(10), false),
                post("expanded", "expanded-post", "정규식 아무거나 문자", BlogPostStatus.PUBLISHED,
                        NOW.minusSeconds(20), NOW.minusSeconds(20), false),
                post("draft-related", "draft-related", "비공개 관련", BlogPostStatus.DRAFT,
                        NOW.minusSeconds(30), NOW.minusSeconds(30), false)));

        var search = blogPostRepository.searchPublicPostsByTitle(
                ".*", NOW, PageRequest.of(0, 20));
        var related = blogPostRepository.findPublicPostsBySlugs(
                List.of("literal-post", "expanded-post", "draft-related"), NOW);

        assertThat(search.getContent())
                .extracting(BlogPost::getId)
                .containsExactly("literal");
        assertThat(related)
                .extracting(BlogPost::getId)
                .containsExactlyInAnyOrder("literal", "expanded");
    }

    @Test
    void newsletterQueryRequiresExplicitTrueAndTreatsMissingFieldAsFalse() {
        blogPostRepository.insert(post(
                "enabled", "enabled-post", "발송 글", BlogPostStatus.PUBLISHED,
                NOW, NOW, true));
        blogPostRepository.insert(post(
                "disabled", "disabled-post", "미발송 글", BlogPostStatus.PUBLISHED,
                NOW, NOW, false));
        mongoTemplate.getCollection("blog_posts").insertOne(new Document()
                .append("_id", "missing")
                .append("slug", "missing-post")
                .append("title", "필드 누락")
                .append("status", BlogPostStatus.PUBLISHED.name())
                .append("publishedAt", Date.from(NOW))
                .append("createdAt", Date.from(NOW))
                .append("updatedAt", Date.from(NOW)));

        assertThat(blogPostRepository.findNewsletterEligiblePostsAfter(null, 100))
                .extracting(BlogPost::getId)
                .containsExactly("enabled");
        assertThat(blogPostRepository.findById("missing").orElseThrow()
                .isNewsletterEnabled()).isFalse();
    }

    private void ensureIndexes() {
        new BlogPostMongoIndexInitializer(mongoTemplate).run(null);
    }

    private List<Document> indexes(String collection) {
        return mongoTemplate.getCollection(collection)
                .listIndexes()
                .into(new ArrayList<>());
    }

    private BlogPost post(
            String id,
            String slug,
            String title,
            BlogPostStatus status,
            Instant publishedAt,
            Instant createdAt,
            boolean newsletterEnabled) {
        return BlogPost.builder()
                .id(id)
                .slug(slug)
                .title(title)
                .summary("요약")
                .contentMarkdown("본문")
                .authorName("토선생")
                .status(status)
                .relatedPostSlugs(List.of())
                .publishedAt(publishedAt)
                .newsletterEnabled(newsletterEnabled)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }
}
