package web.tosunsaeng.domain.newsletter.domain.repository;

import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterCampaign;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterCampaignStatus;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsletterCampaignQueryRepositoryImplTest {

    private static final Instant NOW = Instant.parse("2026-07-31T03:00:00Z");
    private static final Instant EXPIRES_AT = NOW.plusSeconds(300);

    @Mock
    private MongoTemplate mongoTemplate;

    private NewsletterCampaignQueryRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new NewsletterCampaignQueryRepositoryImpl(mongoTemplate);
    }

    @Test
    void scheduledClaimIsSingleAtomicFindAndModifyWithTokenAndExpiry() {
        NewsletterCampaign claimed = campaign(NewsletterCampaignStatus.SENDING, null);
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(NewsletterCampaign.class)))
                .thenReturn(claimed);

        assertThat(repository.claimNextScheduled(NOW, EXPIRES_AT, "claim-token"))
                .contains(claimed);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(
                queryCaptor.capture(),
                updateCaptor.capture(),
                any(FindAndModifyOptions.class),
                eq(NewsletterCampaign.class));
        assertThat(queryCaptor.getValue().getQueryObject())
                .containsEntry("status", NewsletterCampaignStatus.SCHEDULED)
                .containsEntry("scheduledAt", new Document("$lte", NOW));
        assertThat(queryCaptor.getValue().getSortObject())
                .isEqualTo(new Document("scheduledAt", 1).append("_id", 1));
        assertThat(updateCaptor.getValue().getUpdateObject().get("$set", Document.class))
                .containsEntry("status", NewsletterCampaignStatus.SENDING)
                .containsEntry("claimedAt", NOW)
                .containsEntry("claimToken", "claim-token")
                .containsEntry("claimExpiresAt", EXPIRES_AT)
                .containsEntry("updatedAt", NOW);
    }

    @Test
    void staleGenerationReclaimKeepsSendingAndRequiresUnfinishedExpiredClaim() {
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(NewsletterCampaign.class)))
                .thenReturn(campaign(NewsletterCampaignStatus.SENDING, null));

        repository.reclaimNextExpiredGeneration(NOW, EXPIRES_AT, "new-token");

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(
                queryCaptor.capture(),
                updateCaptor.capture(),
                any(FindAndModifyOptions.class),
                eq(NewsletterCampaign.class));
        assertThat(queryCaptor.getValue().getQueryObject())
                .containsEntry("status", NewsletterCampaignStatus.SENDING)
                .containsEntry("totalRecipients", null)
                .containsEntry("claimExpiresAt", new Document("$lte", NOW));
        Document set = updateCaptor.getValue().getUpdateObject().get("$set", Document.class);
        assertThat(set)
                .doesNotContainKey("status")
                .containsEntry("claimToken", "new-token")
                .containsEntry("claimExpiresAt", EXPIRES_AT);
    }

    @Test
    void generationHeartbeatAndFinishAreFencedByCurrentClaimToken() {
        when(mongoTemplate.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(NewsletterCampaign.class)))
                .thenReturn(
                        UpdateResult.acknowledged(1, 0L, null),
                        UpdateResult.acknowledged(1, 1L, null));

        assertThat(repository.extendGenerationClaim(
                "campaign-id", "current-token", EXPIRES_AT, NOW)).isTrue();
        assertThat(repository.finishDeliveryGeneration(
                "campaign-id", "current-token", 123, NOW)).isTrue();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, org.mockito.Mockito.times(2)).updateFirst(
                queryCaptor.capture(),
                updateCaptor.capture(),
                eq(NewsletterCampaign.class));
        queryCaptor.getAllValues().forEach(query -> assertThat(query.getQueryObject())
                .containsEntry("_id", "campaign-id")
                .containsEntry("status", NewsletterCampaignStatus.SENDING)
                .containsEntry("claimToken", "current-token")
                .containsEntry("totalRecipients", null));
        Document finish = updateCaptor.getAllValues().get(1).getUpdateObject();
        assertThat(finish.get("$set", Document.class))
                .containsEntry("totalRecipients", 123L)
                .containsEntry("updatedAt", NOW);
        assertThat(finish.get("$unset", Document.class)).containsKey("claimExpiresAt");
    }

    @Test
    void completionUsesClaimFencingAndPersistsAllCounts() {
        when(mongoTemplate.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(NewsletterCampaign.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        assertThat(repository.completeCampaign(
                "campaign-id",
                "claim-token",
                NewsletterCampaignStatus.FAILED,
                10,
                7,
                2,
                1,
                NOW)).isTrue();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(
                queryCaptor.capture(),
                updateCaptor.capture(),
                eq(NewsletterCampaign.class));
        assertThat(queryCaptor.getValue().getQueryObject())
                .containsEntry("_id", "campaign-id")
                .containsEntry("status", NewsletterCampaignStatus.SENDING)
                .containsEntry("claimToken", "claim-token");
        assertThat(updateCaptor.getValue().getUpdateObject().get("$set", Document.class))
                .containsEntry("status", NewsletterCampaignStatus.FAILED)
                .containsEntry("totalRecipients", 10L)
                .containsEntry("sentCount", 7L)
                .containsEntry("failedCount", 2L)
                .containsEntry("skippedCount", 1L)
                .containsEntry("completedAt", NOW);
    }

    @Test
    void cancellationOnlyMatchesScheduledCampaign() {
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(NewsletterCampaign.class)))
                .thenReturn(campaign(NewsletterCampaignStatus.CANCELED, null));

        assertThat(repository.cancelScheduled("campaign-id", NOW)).isTrue();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(
                queryCaptor.capture(),
                updateCaptor.capture(),
                any(FindAndModifyOptions.class),
                eq(NewsletterCampaign.class));
        assertThat(queryCaptor.getValue().getQueryObject())
                .containsEntry("_id", "campaign-id")
                .containsEntry("status", NewsletterCampaignStatus.SCHEDULED);
        assertThat(updateCaptor.getValue().getUpdateObject().get("$set", Document.class))
                .containsEntry("status", NewsletterCampaignStatus.CANCELED)
                .containsEntry("canceledAt", NOW);
    }

    private NewsletterCampaign campaign(
            NewsletterCampaignStatus status,
            Long totalRecipients) {
        return NewsletterCampaign.builder()
                .id("campaign-id")
                .postId("post-id")
                .status(status)
                .scheduledAt(NOW.minusSeconds(60))
                .claimToken("claim-token")
                .totalRecipients(totalRecipients)
                .createdAt(NOW.minusSeconds(120))
                .updatedAt(NOW)
                .build();
    }
}
