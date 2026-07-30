package web.tosunsaeng.domain.blog.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.blog.domain.entity.BlogPost;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class BlogPostMongoIndexInitializer implements ApplicationRunner {

    static final String SLUG_INDEX_NAME = "uk_blog_posts_slug";
    static final String PUBLICATION_INDEX_NAME = "idx_blog_posts_status_published_at";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        IndexOperations indexOperations = mongoTemplate.indexOps(BlogPost.class);
        indexOperations.ensureIndex(slugIndex());
        indexOperations.ensureIndex(publicationIndex());
        log.info("Ensured MongoDB indexes: {}, {}", SLUG_INDEX_NAME, PUBLICATION_INDEX_NAME);
    }

    static Index slugIndex() {
        return new Index()
                .on("slug", Sort.Direction.ASC)
                .unique()
                .named(SLUG_INDEX_NAME);
    }

    static Index publicationIndex() {
        return new Index()
                .on("status", Sort.Direction.ASC)
                .on("publishedAt", Sort.Direction.DESC)
                .named(PUBLICATION_INDEX_NAME);
    }
}
