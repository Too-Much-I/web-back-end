package web.tosunsaeng.domain.newsletter.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterSubscriber;

import java.util.Optional;

public interface NewsletterSubscriberRepository
        extends MongoRepository<NewsletterSubscriber, String>,
        NewsletterSubscriberQueryRepository {

    Optional<NewsletterSubscriber> findByEmail(String normalizedEmail);
}
