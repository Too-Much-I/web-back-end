package web.tosunsaeng.domain.blog.comment.config;

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
import web.tosunsaeng.domain.blog.comment.domain.entity.AnonymousVisitor;
import web.tosunsaeng.domain.blog.comment.domain.entity.BlogComment;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class BlogCommentMongoIndexInitializer implements ApplicationRunner {

    static final String VISITOR_TOKEN_HASH_INDEX_NAME =
            "uk_anonymous_visitors_token_hash";
    static final String COMMENT_POST_STATUS_CREATED_AT_INDEX_NAME =
            "idx_blog_comments_post_status_created_at";
    static final String COMMENT_VISITOR_INDEX_NAME =
            "idx_blog_comments_anonymous_visitor_id";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        IndexOperations visitorIndexes = mongoTemplate.indexOps(AnonymousVisitor.class);
        visitorIndexes.ensureIndex(visitorTokenHashIndex());

        IndexOperations commentIndexes = mongoTemplate.indexOps(BlogComment.class);
        commentIndexes.ensureIndex(commentPostStatusCreatedAtIndex());
        commentIndexes.ensureIndex(commentVisitorIndex());

        log.info(
                "Ensured MongoDB indexes: {}, {}, {}",
                VISITOR_TOKEN_HASH_INDEX_NAME,
                COMMENT_POST_STATUS_CREATED_AT_INDEX_NAME,
                COMMENT_VISITOR_INDEX_NAME);
    }

    static Index visitorTokenHashIndex() {
        return new Index()
                .on("tokenHash", Sort.Direction.ASC)
                .unique()
                .named(VISITOR_TOKEN_HASH_INDEX_NAME);
    }

    static Index commentPostStatusCreatedAtIndex() {
        return new Index()
                .on("postId", Sort.Direction.ASC)
                .on("status", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.DESC)
                .named(COMMENT_POST_STATUS_CREATED_AT_INDEX_NAME);
    }

    static Index commentVisitorIndex() {
        return new Index()
                .on("anonymousVisitorId", Sort.Direction.ASC)
                .named(COMMENT_VISITOR_INDEX_NAME);
    }
}
