package web.tosunsaeng.domain.newsletter.domain.repository;

import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterDelivery;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterDeliveryStatus;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterFailureType;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsletterDeliveryQueryRepositoryImplTest {

    private static final Instant NOW = Instant.parse("2026-07-31T04:00:00Z");
    private static final Instant EXPIRES_AT = NOW.plusSeconds(300);

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private BulkOperations bulkOperations;

    private NewsletterDeliveryQueryRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new NewsletterDeliveryQueryRepositoryImpl(mongoTemplate);
    }

    @Test
    void unorderedUpsertUsesPostAndSubscriberAsIdempotencyKey() {
        when(mongoTemplate.bulkOps(
                BulkOperations.BulkMode.UNORDERED,
                NewsletterDelivery.class)).thenReturn(bulkOperations);
        when(bulkOperations.upsert(any(Query.class), any(Update.class)))
                .thenReturn(bulkOperations);

        repository.upsertPendingDeliveries(
                "campaign-id",
                "post-id",
                List.of("subscriber-a", "subscriber-b"),
                NOW);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(bulkOperations, times(2)).upsert(
                queryCaptor.capture(),
                updateCaptor.capture());
        verify(bulkOperations).execute();
        assertThat(queryCaptor.getAllValues()).allSatisfy(query ->
                assertThat(query.getQueryObject()).containsEntry("postId", "post-id"));
        assertThat(queryCaptor.getAllValues().get(0).getQueryObject())
                .containsEntry("subscriberId", "subscriber-a");
        Document insert = updateCaptor.getAllValues().get(0)
                .getUpdateObject()
                .get("$setOnInsert", Document.class);
        assertThat(insert)
                .containsEntry("campaignId", "campaign-id")
                .containsEntry("postId", "post-id")
                .containsEntry("subscriberId", "subscriber-a")
                .containsEntry("status", NewsletterDeliveryStatus.PENDING)
                .containsEntry("attemptCount", 0)
                .containsEntry("retryable", false);
    }

    @Test
    void emptyRecipientBatchDoesNotOpenMongoBulkOperation() {
        repository.upsertPendingDeliveries("campaign-id", "post-id", List.of(), NOW);

        verifyNoInteractions(mongoTemplate, bulkOperations);
    }

    @Test
    void pendingAndRetryClaimsUseAtomicStateAndDueConditions() {
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(NewsletterDelivery.class)))
                .thenReturn(delivery(NewsletterDeliveryStatus.SENDING, 1));

        repository.claimNextPending(NOW, EXPIRES_AT, "pending-token");
        repository.claimNextRetry(NOW, EXPIRES_AT, "retry-token", 4);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(2)).findAndModify(
                queryCaptor.capture(),
                updateCaptor.capture(),
                any(FindAndModifyOptions.class),
                eq(NewsletterDelivery.class));
        assertThat(queryCaptor.getAllValues().get(0).getQueryObject())
                .containsEntry("status", NewsletterDeliveryStatus.PENDING);
        assertThat(queryCaptor.getAllValues().get(1).getQueryObject())
                .containsEntry("status", NewsletterDeliveryStatus.FAILED)
                .containsEntry("retryable", true)
                .containsEntry("nextRetryAt", new Document("$lte", NOW))
                .containsEntry("attemptCount", new Document("$lt", 4));
        assertThat(updateCaptor.getAllValues()).allSatisfy(update ->
                assertThat(update.getUpdateObject().get("$set", Document.class))
                        .containsEntry("status", NewsletterDeliveryStatus.SENDING)
                        .containsKey("claimToken")
                        .containsEntry("claimExpiresAt", EXPIRES_AT));
    }

    @Test
    void providerStartIncrementsAttemptImmediatelyBeforeCallWithClaimFencing() {
        NewsletterDelivery started = delivery(NewsletterDeliveryStatus.SENDING, 2);
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(NewsletterDelivery.class))).thenReturn(started);

        assertThat(repository.markProviderCallStarted(
                "delivery-id", "claim-token", NOW, EXPIRES_AT)).contains(started);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(
                queryCaptor.capture(),
                updateCaptor.capture(),
                any(FindAndModifyOptions.class),
                eq(NewsletterDelivery.class));
        assertThat(queryCaptor.getValue().getQueryObject())
                .containsEntry("_id", "delivery-id")
                .containsEntry("status", NewsletterDeliveryStatus.SENDING)
                .containsEntry("claimToken", "claim-token")
                .containsEntry("providerCallStartedAt", null);
        assertThat(updateCaptor.getValue().getUpdateObject().get("$inc", Document.class))
                .containsEntry("attemptCount", 1);
        assertThat(updateCaptor.getValue().getUpdateObject().get("$set", Document.class))
                .containsEntry("providerCallStartedAt", NOW)
                .containsEntry("claimExpiresAt", EXPIRES_AT);
    }

    @Test
    void releasingClaimBeforeProviderIsFencedAndDoesNotTouchAttemptCount() {
        when(mongoTemplate.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(NewsletterDelivery.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        assertThat(repository.releaseClaimToPending(
                "delivery-id", "claim-token", NOW)).isTrue();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(
                queryCaptor.capture(),
                updateCaptor.capture(),
                eq(NewsletterDelivery.class));
        assertThat(queryCaptor.getValue().getQueryObject())
                .containsEntry("status", NewsletterDeliveryStatus.SENDING)
                .containsEntry("claimToken", "claim-token")
                .containsEntry("providerCallStartedAt", null);
        Document update = updateCaptor.getValue().getUpdateObject();
        assertThat(update.get("$set", Document.class))
                .containsEntry("status", NewsletterDeliveryStatus.PENDING);
        assertThat(update.get("$unset", Document.class).keySet())
                .containsExactlyInAnyOrder("claimToken", "claimExpiresAt");
        assertThat(update).doesNotContainKey("$inc");
    }

    @Test
    void staleRecoverySeparatesBeforeAndAfterProviderBoundary() {
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(NewsletterDelivery.class)))
                .thenReturn(
                        delivery(NewsletterDeliveryStatus.PENDING, 0),
                        delivery(NewsletterDeliveryStatus.FAILED, 1));

        repository.recoverNextStaleBeforeProvider(NOW);
        repository.failNextStaleAfterProvider(NOW);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(2)).findAndModify(
                queryCaptor.capture(),
                updateCaptor.capture(),
                any(FindAndModifyOptions.class),
                eq(NewsletterDelivery.class));
        assertThat(queryCaptor.getAllValues().get(0).getQueryObject())
                .containsEntry("status", NewsletterDeliveryStatus.SENDING)
                .containsEntry("claimExpiresAt", new Document("$lte", NOW))
                .containsEntry("providerCallStartedAt", null);
        Document afterProviderQuery = queryCaptor.getAllValues().get(1).getQueryObject();
        assertThat(afterProviderQuery.get("providerCallStartedAt", Document.class))
                .containsEntry("$exists", true)
                .containsEntry("$ne", null);
        Document unknown = updateCaptor.getAllValues().get(1)
                .getUpdateObject()
                .get("$set", Document.class);
        assertThat(unknown)
                .containsEntry("status", NewsletterDeliveryStatus.FAILED)
                .containsEntry("lastErrorCode", NewsletterFailureType.PROVIDER_RESULT_UNKNOWN)
                .containsEntry("retryable", false)
                .containsEntry("failedAt", NOW);
    }

    @Test
    void terminalUpdatesRequireCurrentClaimToken() {
        when(mongoTemplate.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(NewsletterDelivery.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        assertThat(repository.markSent(
                "delivery-id", "current-token", "ses-message-id", NOW)).isTrue();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(
                queryCaptor.capture(),
                updateCaptor.capture(),
                eq(NewsletterDelivery.class));
        assertThat(queryCaptor.getValue().getQueryObject())
                .containsEntry("_id", "delivery-id")
                .containsEntry("status", NewsletterDeliveryStatus.SENDING)
                .containsEntry("claimToken", "current-token");
        assertThat(updateCaptor.getValue().getUpdateObject().get("$set", Document.class))
                .containsEntry("status", NewsletterDeliveryStatus.SENT)
                .containsEntry("providerMessageId", "ses-message-id")
                .containsEntry("sentAt", NOW)
                .containsEntry("retryable", false);
    }

    @Test
    void manualRetryCannotSelectUnknownResultOrExhaustedAttempt() {
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(NewsletterDelivery.class)))
                .thenReturn(null);

        assertThat(repository.requeueFailedForManualRetry(
                "delivery-id", 4, NOW)).isEmpty();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findAndModify(
                queryCaptor.capture(),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(NewsletterDelivery.class));
        assertThat(queryCaptor.getValue().getQueryObject())
                .containsEntry("status", NewsletterDeliveryStatus.FAILED)
                .containsEntry("retryable", true)
                .containsEntry("attemptCount", new Document("$lt", 4))
                .containsEntry("lastErrorCode", new Document(
                        "$ne", NewsletterFailureType.PROVIDER_RESULT_UNKNOWN));
    }

    @Test
    void campaignCountsTreatOnlyNonRetryableOrExhaustedFailuresAsTerminal() {
        when(mongoTemplate.count(any(Query.class), eq(NewsletterDelivery.class)))
                .thenReturn(4L, 2L, 1L, 1L);

        NewsletterDeliveryQueryRepository.DeliveryCounts counts =
                repository.countByCampaign("campaign-id", 4);

        assertThat(counts.total()).isEqualTo(4);
        assertThat(counts.sent()).isEqualTo(2);
        assertThat(counts.skipped()).isEqualTo(1);
        assertThat(counts.terminalFailed()).isEqualTo(1);
        assertThat(counts.terminalTotal()).isEqualTo(4);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate, times(4)).count(
                captor.capture(), eq(NewsletterDelivery.class));
        Document failureQuery = captor.getAllValues().get(3).getQueryObject();
        @SuppressWarnings("unchecked")
        List<Document> campaignAndState =
                (List<Document>) failureQuery.get("$and");
        assertThat(campaignAndState.get(0)).containsEntry("campaignId", "campaign-id");
        @SuppressWarnings("unchecked")
        List<Document> terminalAnd =
                (List<Document>) campaignAndState.get(1).get("$and");
        assertThat(terminalAnd.get(0))
                .containsEntry("status", NewsletterDeliveryStatus.FAILED);
        @SuppressWarnings("unchecked")
        List<Document> terminalOr =
                (List<Document>) terminalAnd.get(1).get("$or");
        assertThat(terminalOr)
                .contains(new Document("retryable", false))
                .contains(new Document(
                        "attemptCount", new Document("$gte", 4)));
    }

    private NewsletterDelivery delivery(NewsletterDeliveryStatus status, int attempts) {
        return NewsletterDelivery.builder()
                .id("delivery-id")
                .campaignId("campaign-id")
                .postId("post-id")
                .subscriberId("subscriber-id")
                .status(status)
                .attemptCount(attempts)
                .claimToken("claim-token")
                .claimExpiresAt(EXPIRES_AT)
                .createdAt(NOW.minusSeconds(60))
                .updatedAt(NOW)
                .build();
    }
}
