package web.tosunsaeng.domain.newsletter.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterDelivery;

public interface NewsletterDeliveryRepository
        extends MongoRepository<NewsletterDelivery, String>,
        NewsletterDeliveryQueryRepository {
}
