package web.tosunsaeng.domain.newsletter.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterCampaign;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterCampaignStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NewsletterCampaignRepository
        extends MongoRepository<NewsletterCampaign, String>,
        NewsletterCampaignQueryRepository {

    Optional<NewsletterCampaign> findByPostId(String postId);

    List<NewsletterCampaign> findByPostIdIn(Collection<String> postIds);

    boolean existsByIdAndStatus(String id, NewsletterCampaignStatus status);
}
