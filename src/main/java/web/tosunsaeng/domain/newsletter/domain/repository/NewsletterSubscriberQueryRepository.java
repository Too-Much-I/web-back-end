package web.tosunsaeng.domain.newsletter.domain.repository;

import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterSubscriber;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NewsletterSubscriberQueryRepository {

    Optional<NewsletterSubscriber> reactivateByEmail(String normalizedEmail, Instant now);

    Optional<NewsletterSubscriber> unsubscribeByIdAndVersion(
            String subscriberId,
            long tokenVersion,
            Instant now);

    List<NewsletterSubscriber> findActiveAfterId(String lastSeenId, int limit);
}
