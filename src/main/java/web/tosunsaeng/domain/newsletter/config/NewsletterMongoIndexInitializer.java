package web.tosunsaeng.domain.newsletter.config;

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
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterSubscriber;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class NewsletterMongoIndexInitializer implements ApplicationRunner {

    static final String EMAIL_INDEX_NAME = "uk_newsletter_subscribers_email";
    static final String STATUS_INDEX_NAME = "idx_newsletter_subscribers_status";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        IndexOperations indexes = mongoTemplate.indexOps(NewsletterSubscriber.class);
        indexes.ensureIndex(emailIndex());
        indexes.ensureIndex(statusIndex());
        log.info("Ensured MongoDB indexes: {}, {}", EMAIL_INDEX_NAME, STATUS_INDEX_NAME);
    }

    static Index emailIndex() {
        return new Index()
                .on("email", Sort.Direction.ASC)
                .unique()
                .named(EMAIL_INDEX_NAME);
    }

    static Index statusIndex() {
        return new Index()
                .on("status", Sort.Direction.ASC)
                .named(STATUS_INDEX_NAME);
    }
}
