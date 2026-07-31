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
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterCampaign;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterDelivery;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class NewsletterMongoIndexInitializer implements ApplicationRunner {

    static final String EMAIL_INDEX_NAME = "uk_newsletter_subscribers_email";
    static final String STATUS_INDEX_NAME = "idx_newsletter_subscribers_status";
    static final String CAMPAIGN_POST_INDEX_NAME = "uk_newsletter_campaigns_post_id";
    static final String CAMPAIGN_SCHEDULE_INDEX_NAME =
            "idx_newsletter_campaigns_status_scheduled_at";
    static final String CAMPAIGN_CLAIM_INDEX_NAME =
            "idx_newsletter_campaigns_status_claim_expires_at";
    static final String CAMPAIGN_UPDATED_INDEX_NAME =
            "idx_newsletter_campaigns_status_updated_at";
    static final String DELIVERY_RECIPIENT_INDEX_NAME =
            "uk_newsletter_deliveries_post_subscriber";
    static final String DELIVERY_CAMPAIGN_INDEX_NAME =
            "idx_newsletter_deliveries_campaign_status";
    static final String DELIVERY_RETRY_INDEX_NAME =
            "idx_newsletter_deliveries_status_next_retry_at";
    static final String DELIVERY_CLAIM_INDEX_NAME =
            "idx_newsletter_deliveries_status_claim_expires_at";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        IndexOperations indexes = mongoTemplate.indexOps(NewsletterSubscriber.class);
        indexes.ensureIndex(emailIndex());
        indexes.ensureIndex(statusIndex());

        IndexOperations campaignIndexes = mongoTemplate.indexOps(NewsletterCampaign.class);
        campaignIndexes.ensureIndex(campaignPostIndex());
        campaignIndexes.ensureIndex(campaignScheduleIndex());
        campaignIndexes.ensureIndex(campaignClaimIndex());
        campaignIndexes.ensureIndex(campaignUpdatedIndex());

        IndexOperations deliveryIndexes = mongoTemplate.indexOps(NewsletterDelivery.class);
        deliveryIndexes.ensureIndex(deliveryRecipientIndex());
        deliveryIndexes.ensureIndex(deliveryCampaignIndex());
        deliveryIndexes.ensureIndex(deliveryRetryIndex());
        deliveryIndexes.ensureIndex(deliveryClaimIndex());
        log.info("Ensured newsletter subscriber, campaign and delivery MongoDB indexes");
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

    static Index campaignPostIndex() {
        return new Index()
                .on("postId", Sort.Direction.ASC)
                .unique()
                .named(CAMPAIGN_POST_INDEX_NAME);
    }

    static Index campaignScheduleIndex() {
        return new Index()
                .on("status", Sort.Direction.ASC)
                .on("scheduledAt", Sort.Direction.ASC)
                .named(CAMPAIGN_SCHEDULE_INDEX_NAME);
    }

    static Index campaignClaimIndex() {
        return new Index()
                .on("status", Sort.Direction.ASC)
                .on("claimExpiresAt", Sort.Direction.ASC)
                .named(CAMPAIGN_CLAIM_INDEX_NAME);
    }

    static Index campaignUpdatedIndex() {
        return new Index()
                .on("status", Sort.Direction.ASC)
                .on("updatedAt", Sort.Direction.ASC)
                .named(CAMPAIGN_UPDATED_INDEX_NAME);
    }

    static Index deliveryRecipientIndex() {
        return new Index()
                .on("postId", Sort.Direction.ASC)
                .on("subscriberId", Sort.Direction.ASC)
                .unique()
                .named(DELIVERY_RECIPIENT_INDEX_NAME);
    }

    static Index deliveryCampaignIndex() {
        return new Index()
                .on("campaignId", Sort.Direction.ASC)
                .on("status", Sort.Direction.ASC)
                .named(DELIVERY_CAMPAIGN_INDEX_NAME);
    }

    static Index deliveryRetryIndex() {
        return new Index()
                .on("status", Sort.Direction.ASC)
                .on("nextRetryAt", Sort.Direction.ASC)
                .named(DELIVERY_RETRY_INDEX_NAME);
    }

    static Index deliveryClaimIndex() {
        return new Index()
                .on("status", Sort.Direction.ASC)
                .on("claimExpiresAt", Sort.Direction.ASC)
                .named(DELIVERY_CLAIM_INDEX_NAME);
    }
}
