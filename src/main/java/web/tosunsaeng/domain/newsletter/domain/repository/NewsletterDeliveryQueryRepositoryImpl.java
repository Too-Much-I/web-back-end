package web.tosunsaeng.domain.newsletter.domain.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.BulkOperationException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterDelivery;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterDeliveryStatus;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterFailureType;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class NewsletterDeliveryQueryRepositoryImpl
        implements NewsletterDeliveryQueryRepository {

    private static final int DUPLICATE_KEY_CODE = 11_000;

    private final MongoTemplate mongoTemplate;

    @Override
    public void upsertPendingDeliveries(
            String campaignId,
            String postId,
            Collection<String> subscriberIds,
            Instant now) {
        if (subscriberIds.isEmpty()) {
            return;
        }
        BulkOperations operations = mongoTemplate.bulkOps(
                BulkOperations.BulkMode.UNORDERED,
                NewsletterDelivery.class);
        subscriberIds.forEach(subscriberId -> operations.upsert(
                Query.query(Criteria.where("postId").is(postId)
                        .and("subscriberId").is(subscriberId)),
                new Update()
                        .setOnInsert("campaignId", campaignId)
                        .setOnInsert("postId", postId)
                        .setOnInsert("subscriberId", subscriberId)
                        .setOnInsert("status", NewsletterDeliveryStatus.PENDING)
                        .setOnInsert("attemptCount", 0)
                        .setOnInsert("retryable", false)
                        .setOnInsert("createdAt", now)
                        .setOnInsert("updatedAt", now)));
        try {
            operations.execute();
        } catch (BulkOperationException exception) {
            if (!exception.getErrors().isEmpty()
                    && exception.getErrors().stream()
                    .allMatch(error -> error.getCode() == DUPLICATE_KEY_CODE)) {
                return;
            }
            throw exception;
        }
    }

    @Override
    public Optional<NewsletterDelivery> claimNextPending(
            Instant now,
            Instant claimExpiresAt,
            String claimToken) {
        Query query = Query.query(Criteria.where("status")
                        .is(NewsletterDeliveryStatus.PENDING))
                .with(Sort.by(Sort.Order.asc("_id")));
        return claim(query, now, claimExpiresAt, claimToken);
    }

    @Override
    public Optional<NewsletterDelivery> claimNextRetry(
            Instant now,
            Instant claimExpiresAt,
            String claimToken,
            int maxAttempts) {
        Query query = Query.query(Criteria.where("status")
                        .is(NewsletterDeliveryStatus.FAILED)
                        .and("retryable").is(true)
                        .and("nextRetryAt").lte(now)
                        .and("attemptCount").lt(maxAttempts))
                .with(Sort.by(
                        Sort.Order.asc("nextRetryAt"),
                        Sort.Order.asc("_id")));
        return claim(query, now, claimExpiresAt, claimToken);
    }

    @Override
    public boolean releaseClaimToPending(
            String deliveryId,
            String claimToken,
            Instant now) {
        Query query = claimedSendingQuery(deliveryId, claimToken)
                .addCriteria(Criteria.where("providerCallStartedAt").is(null));
        Update update = new Update()
                .set("status", NewsletterDeliveryStatus.PENDING)
                .unset("claimToken")
                .unset("claimExpiresAt")
                .set("updatedAt", now);
        return mongoTemplate.updateFirst(query, update, NewsletterDelivery.class)
                .getModifiedCount() == 1;
    }

    @Override
    public Optional<NewsletterDelivery> markProviderCallStarted(
            String deliveryId,
            String claimToken,
            Instant now,
            Instant claimExpiresAt) {
        Query query = claimedSendingQuery(deliveryId, claimToken)
                .addCriteria(Criteria.where("providerCallStartedAt").is(null));
        Update update = new Update()
                .set("providerCallStartedAt", now)
                .set("claimExpiresAt", claimExpiresAt)
                .inc("attemptCount", 1)
                .set("updatedAt", now);
        return findAndModify(query, update);
    }

    @Override
    public boolean markSent(
            String deliveryId,
            String claimToken,
            String providerMessageId,
            Instant now) {
        Update update = new Update()
                .set("status", NewsletterDeliveryStatus.SENT)
                .set("providerMessageId", providerMessageId)
                .set("sentAt", now)
                .set("retryable", false)
                .unset("nextRetryAt")
                .unset("failedAt")
                .unset("lastErrorCode")
                .unset("claimToken")
                .unset("claimExpiresAt")
                .set("updatedAt", now);
        return updateClaimed(deliveryId, claimToken, update);
    }

    @Override
    public boolean markFailed(
            String deliveryId,
            String claimToken,
            NewsletterFailureType failureType,
            boolean retryable,
            Instant nextRetryAt,
            Instant now) {
        Update update = new Update()
                .set("status", NewsletterDeliveryStatus.FAILED)
                .set("lastErrorCode", failureType)
                .set("retryable", retryable)
                .set("failedAt", now)
                .unset("claimToken")
                .unset("claimExpiresAt")
                .set("updatedAt", now);
        if (nextRetryAt == null) {
            update.unset("nextRetryAt");
        } else {
            update.set("nextRetryAt", nextRetryAt);
        }
        return updateClaimed(deliveryId, claimToken, update);
    }

    @Override
    public boolean markSkipped(String deliveryId, String claimToken, Instant now) {
        Update update = new Update()
                .set("status", NewsletterDeliveryStatus.SKIPPED)
                .set("skippedAt", now)
                .set("retryable", false)
                .unset("nextRetryAt")
                .unset("claimToken")
                .unset("claimExpiresAt")
                .set("updatedAt", now);
        return updateClaimed(deliveryId, claimToken, update);
    }

    @Override
    public Optional<NewsletterDelivery> recoverNextStaleBeforeProvider(Instant now) {
        Query query = Query.query(Criteria.where("status")
                        .is(NewsletterDeliveryStatus.SENDING)
                        .and("claimExpiresAt").lte(now)
                        .and("providerCallStartedAt").is(null))
                .with(Sort.by(
                        Sort.Order.asc("claimExpiresAt"),
                        Sort.Order.asc("_id")));
        Update update = new Update()
                .set("status", NewsletterDeliveryStatus.PENDING)
                .unset("claimToken")
                .unset("claimExpiresAt")
                .set("updatedAt", now);
        return findAndModify(query, update);
    }

    @Override
    public Optional<NewsletterDelivery> failNextStaleAfterProvider(Instant now) {
        Query query = Query.query(Criteria.where("status")
                        .is(NewsletterDeliveryStatus.SENDING)
                        .and("claimExpiresAt").lte(now)
                        .and("providerCallStartedAt").exists(true).ne(null))
                .with(Sort.by(
                        Sort.Order.asc("claimExpiresAt"),
                        Sort.Order.asc("_id")));
        Update update = new Update()
                .set("status", NewsletterDeliveryStatus.FAILED)
                .set("lastErrorCode", NewsletterFailureType.PROVIDER_RESULT_UNKNOWN)
                .set("retryable", false)
                .set("failedAt", now)
                .unset("nextRetryAt")
                .unset("claimToken")
                .unset("claimExpiresAt")
                .set("updatedAt", now);
        return findAndModify(query, update);
    }

    @Override
    public Optional<NewsletterDelivery> requeueFailedForManualRetry(
            String deliveryId,
            int maxAttempts,
            Instant now) {
        Query query = Query.query(Criteria.where("_id").is(deliveryId)
                .and("status").is(NewsletterDeliveryStatus.FAILED)
                .and("retryable").is(true)
                .and("attemptCount").lt(maxAttempts)
                .and("lastErrorCode").ne(NewsletterFailureType.PROVIDER_RESULT_UNKNOWN));
        Update update = new Update()
                .set("status", NewsletterDeliveryStatus.PENDING)
                .set("retryable", false)
                .unset("nextRetryAt")
                .unset("claimToken")
                .unset("claimExpiresAt")
                .unset("providerCallStartedAt")
                .unset("failedAt")
                .unset("lastErrorCode")
                .set("updatedAt", now);
        return findAndModify(query, update);
    }

    @Override
    public List<String> findManualRetryCandidateIds(
            String campaignId,
            int maxAttempts,
            int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("manual retry 조회 limit은 양수여야 합니다.");
        }
        Query query = Query.query(Criteria.where("campaignId").is(campaignId)
                        .and("status").is(NewsletterDeliveryStatus.FAILED)
                        .and("retryable").is(true)
                        .and("attemptCount").lt(maxAttempts)
                        .and("lastErrorCode")
                        .ne(NewsletterFailureType.PROVIDER_RESULT_UNKNOWN))
                .with(Sort.by(Sort.Order.asc("_id")))
                .limit(limit);
        query.fields().include("_id");
        return mongoTemplate.find(query, NewsletterDelivery.class).stream()
                .map(NewsletterDelivery::getId)
                .toList();
    }

    @Override
    public boolean skipFailedInactive(
            String deliveryId,
            int maxAttempts,
            Instant now) {
        Query query = Query.query(Criteria.where("_id").is(deliveryId)
                .and("status").is(NewsletterDeliveryStatus.FAILED)
                .and("retryable").is(true)
                .and("attemptCount").lt(maxAttempts)
                .and("lastErrorCode")
                .ne(NewsletterFailureType.PROVIDER_RESULT_UNKNOWN));
        Update update = new Update()
                .set("status", NewsletterDeliveryStatus.SKIPPED)
                .set("retryable", false)
                .set("skippedAt", now)
                .unset("nextRetryAt")
                .unset("failedAt")
                .unset("lastErrorCode")
                .set("updatedAt", now);
        return mongoTemplate.updateFirst(query, update, NewsletterDelivery.class)
                .getModifiedCount() == 1;
    }

    @Override
    public DeliveryCounts countByCampaign(String campaignId, int maxAttempts) {
        Criteria campaign = Criteria.where("campaignId").is(campaignId);
        long total = mongoTemplate.count(Query.query(campaign), NewsletterDelivery.class);
        long sent = count(campaignId, Criteria.where("status")
                .is(NewsletterDeliveryStatus.SENT));
        long skipped = count(campaignId, Criteria.where("status")
                .is(NewsletterDeliveryStatus.SKIPPED));
        Criteria terminalFailed = new Criteria().andOperator(
                Criteria.where("status").is(NewsletterDeliveryStatus.FAILED),
                new Criteria().orOperator(
                        Criteria.where("retryable").is(false),
                        Criteria.where("attemptCount").gte(maxAttempts)));
        long failed = count(campaignId, terminalFailed);
        return new DeliveryCounts(total, sent, failed, skipped);
    }

    private Optional<NewsletterDelivery> claim(
            Query query,
            Instant now,
            Instant claimExpiresAt,
            String claimToken) {
        Update update = new Update()
                .set("status", NewsletterDeliveryStatus.SENDING)
                .set("claimToken", claimToken)
                .set("claimExpiresAt", claimExpiresAt)
                .set("retryable", false)
                .unset("nextRetryAt")
                .unset("providerCallStartedAt")
                .unset("failedAt")
                .unset("lastErrorCode")
                .set("updatedAt", now);
        return findAndModify(query, update);
    }

    private Optional<NewsletterDelivery> findAndModify(Query query, Update update) {
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                NewsletterDelivery.class));
    }

    private boolean updateClaimed(
            String deliveryId,
            String claimToken,
            Update update) {
        return mongoTemplate.updateFirst(
                        claimedSendingQuery(deliveryId, claimToken),
                        update,
                        NewsletterDelivery.class)
                .getModifiedCount() == 1;
    }

    private Query claimedSendingQuery(String deliveryId, String claimToken) {
        return Query.query(Criteria.where("_id").is(deliveryId)
                .and("status").is(NewsletterDeliveryStatus.SENDING)
                .and("claimToken").is(claimToken));
    }

    private long count(String campaignId, Criteria stateCriteria) {
        return mongoTemplate.count(
                Query.query(new Criteria().andOperator(
                        Criteria.where("campaignId").is(campaignId),
                        stateCriteria)),
                NewsletterDelivery.class);
    }
}
