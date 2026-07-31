package web.tosunsaeng.domain.newsletter.domain.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterCampaign;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterCampaignStatus;

import java.time.Instant;
import java.util.Optional;

@RequiredArgsConstructor
public class NewsletterCampaignQueryRepositoryImpl
        implements NewsletterCampaignQueryRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public Optional<NewsletterCampaign> claimNextScheduled(
            Instant now,
            Instant claimExpiresAt,
            String claimToken) {
        Query query = Query.query(Criteria.where("status")
                        .is(NewsletterCampaignStatus.SCHEDULED)
                        .and("scheduledAt").lte(now))
                .with(Sort.by(
                        Sort.Order.asc("scheduledAt"),
                        Sort.Order.asc("_id")));
        Update update = claimUpdate(now, claimExpiresAt, claimToken)
                .set("status", NewsletterCampaignStatus.SENDING);
        return findAndModify(query, update);
    }

    @Override
    public Optional<NewsletterCampaign> reclaimNextExpiredGeneration(
            Instant now,
            Instant claimExpiresAt,
            String claimToken) {
        Query query = Query.query(Criteria.where("status")
                        .is(NewsletterCampaignStatus.SENDING)
                        .and("totalRecipients").is(null)
                        .and("claimExpiresAt").lte(now))
                .with(Sort.by(
                        Sort.Order.asc("claimExpiresAt"),
                        Sort.Order.asc("_id")));
        return findAndModify(query, claimUpdate(now, claimExpiresAt, claimToken));
    }

    @Override
    public boolean extendGenerationClaim(
            String campaignId,
            String claimToken,
            Instant claimExpiresAt,
            Instant now) {
        Query query = claimedSendingQuery(campaignId, claimToken)
                .addCriteria(Criteria.where("totalRecipients").is(null));
        Update update = new Update()
                .set("claimExpiresAt", claimExpiresAt)
                .set("updatedAt", now);
        return mongoTemplate.updateFirst(query, update, NewsletterCampaign.class)
                .getMatchedCount() == 1;
    }

    @Override
    public boolean finishDeliveryGeneration(
            String campaignId,
            String claimToken,
            long totalRecipients,
            Instant now) {
        Query query = claimedSendingQuery(campaignId, claimToken)
                .addCriteria(Criteria.where("totalRecipients").is(null));
        Update update = new Update()
                .set("totalRecipients", totalRecipients)
                .unset("claimExpiresAt")
                .set("updatedAt", now);
        return mongoTemplate.updateFirst(query, update, NewsletterCampaign.class)
                .getModifiedCount() == 1;
    }

    @Override
    public Optional<NewsletterCampaign> findNextReadyForCompletion() {
        Query query = Query.query(Criteria.where("status")
                        .is(NewsletterCampaignStatus.SENDING)
                        .and("totalRecipients").exists(true).ne(null))
                .with(Sort.by(
                        Sort.Order.asc("updatedAt"),
                        Sort.Order.asc("_id")))
                .limit(1);
        return Optional.ofNullable(mongoTemplate.findOne(
                query,
                NewsletterCampaign.class));
    }

    @Override
    public boolean touchSendingCampaign(
            String campaignId,
            String claimToken,
            Instant now) {
        return mongoTemplate.updateFirst(
                        claimedSendingQuery(campaignId, claimToken),
                        new Update().set("updatedAt", now),
                        NewsletterCampaign.class)
                .getModifiedCount() == 1;
    }

    @Override
    public boolean completeCampaign(
            String campaignId,
            String claimToken,
            NewsletterCampaignStatus terminalStatus,
            long totalRecipients,
            long sentCount,
            long failedCount,
            long skippedCount,
            Instant now) {
        if (terminalStatus != NewsletterCampaignStatus.SENT
                && terminalStatus != NewsletterCampaignStatus.FAILED) {
            throw new IllegalArgumentException("Campaign terminal 상태가 올바르지 않습니다.");
        }
        Query query = claimedSendingQuery(campaignId, claimToken);
        Update update = new Update()
                .set("status", terminalStatus)
                .set("totalRecipients", totalRecipients)
                .set("sentCount", sentCount)
                .set("failedCount", failedCount)
                .set("skippedCount", skippedCount)
                .set("completedAt", now)
                .unset("claimExpiresAt")
                .set("updatedAt", now);
        return mongoTemplate.updateFirst(query, update, NewsletterCampaign.class)
                .getModifiedCount() == 1;
    }

    @Override
    public boolean cancelScheduled(String campaignId, Instant now) {
        Query query = Query.query(Criteria.where("_id").is(campaignId)
                .and("status").is(NewsletterCampaignStatus.SCHEDULED));
        Update update = new Update()
                .set("status", NewsletterCampaignStatus.CANCELED)
                .set("canceledAt", now)
                .set("updatedAt", now);
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                NewsletterCampaign.class) != null;
    }

    @Override
    public boolean reopenFailed(String campaignId, Instant now) {
        Query query = Query.query(Criteria.where("_id").is(campaignId)
                .and("status").is(NewsletterCampaignStatus.FAILED));
        Update update = new Update()
                .set("status", NewsletterCampaignStatus.SENDING)
                .unset("completedAt")
                .set("updatedAt", now);
        return mongoTemplate.updateFirst(query, update, NewsletterCampaign.class)
                .getModifiedCount() == 1;
    }

    private Optional<NewsletterCampaign> findAndModify(Query query, Update update) {
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                NewsletterCampaign.class));
    }

    private Query claimedSendingQuery(String campaignId, String claimToken) {
        return Query.query(Criteria.where("_id").is(campaignId)
                .and("status").is(NewsletterCampaignStatus.SENDING)
                .and("claimToken").is(claimToken));
    }

    private Update claimUpdate(
            Instant now,
            Instant claimExpiresAt,
            String claimToken) {
        return new Update()
                .set("claimedAt", now)
                .set("claimToken", claimToken)
                .set("claimExpiresAt", claimExpiresAt)
                .set("updatedAt", now);
    }
}
