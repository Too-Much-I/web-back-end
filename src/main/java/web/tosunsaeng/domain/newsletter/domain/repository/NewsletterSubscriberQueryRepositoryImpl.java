package web.tosunsaeng.domain.newsletter.domain.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterSubscriber;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterSubscriberStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class NewsletterSubscriberQueryRepositoryImpl
        implements NewsletterSubscriberQueryRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public Optional<NewsletterSubscriber> reactivateByEmail(
            String normalizedEmail,
            Instant now) {
        Query query = Query.query(Criteria.where("email").is(normalizedEmail)
                .and("status").is(NewsletterSubscriberStatus.UNSUBSCRIBED));
        Update update = new Update()
                .set("status", NewsletterSubscriberStatus.ACTIVE)
                .set("consentAt", now)
                .set("subscribedAt", now)
                .unset("unsubscribedAt")
                .set("updatedAt", now)
                .inc("tokenVersion", 1L);
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                NewsletterSubscriber.class));
    }

    @Override
    public Optional<NewsletterSubscriber> unsubscribeByIdAndVersion(
            String subscriberId,
            long tokenVersion,
            Instant now) {
        Query query = Query.query(Criteria.where("_id").is(subscriberId)
                .and("tokenVersion").is(tokenVersion)
                .and("status").in(
                        NewsletterSubscriberStatus.ACTIVE,
                        NewsletterSubscriberStatus.BOUNCED));
        Update update = new Update()
                .set("status", NewsletterSubscriberStatus.UNSUBSCRIBED)
                .set("unsubscribedAt", now)
                .set("updatedAt", now);
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                NewsletterSubscriber.class));
    }

    @Override
    public List<NewsletterSubscriber> findActiveAfterId(String lastSeenId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        Criteria active = Criteria.where("status").is(NewsletterSubscriberStatus.ACTIVE);
        Criteria criteria = lastSeenId == null
                ? active
                : new Criteria().andOperator(
                        active,
                        Criteria.where("_id").gt(lastSeenId));
        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Order.asc("_id")))
                .limit(limit);
        return mongoTemplate.find(query, NewsletterSubscriber.class);
    }
}
