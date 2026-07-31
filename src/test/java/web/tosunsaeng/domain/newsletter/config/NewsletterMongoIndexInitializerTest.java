package web.tosunsaeng.domain.newsletter.config;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterCampaign;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterDelivery;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterSubscriber;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsletterMongoIndexInitializerTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private IndexOperations subscriberIndexOperations;

    @Mock
    private IndexOperations campaignIndexOperations;

    @Mock
    private IndexOperations deliveryIndexOperations;

    @Test
    void ensuresSubscriberCampaignAndDeliveryIndexesIdempotently() throws Exception {
        when(mongoTemplate.indexOps(NewsletterSubscriber.class))
                .thenReturn(subscriberIndexOperations);
        when(mongoTemplate.indexOps(NewsletterCampaign.class))
                .thenReturn(campaignIndexOperations);
        when(mongoTemplate.indexOps(NewsletterDelivery.class))
                .thenReturn(deliveryIndexOperations);
        NewsletterMongoIndexInitializer initializer =
                new NewsletterMongoIndexInitializer(mongoTemplate);

        initializer.run(null);
        initializer.run(null);

        ArgumentCaptor<IndexDefinition> subscriberCaptor =
                ArgumentCaptor.forClass(IndexDefinition.class);
        verify(subscriberIndexOperations, times(4)).ensureIndex(subscriberCaptor.capture());
        List<IndexDefinition> subscriberDefinitions = subscriberCaptor.getAllValues();
        assertEmailIndex(subscriberDefinitions.get(0));
        assertStatusIndex(subscriberDefinitions.get(1));
        assertThat(subscriberDefinitions.get(2).getIndexKeys())
                .isEqualTo(subscriberDefinitions.get(0).getIndexKeys());
        assertThat(subscriberDefinitions.get(3).getIndexKeys())
                .isEqualTo(subscriberDefinitions.get(1).getIndexKeys());

        ArgumentCaptor<IndexDefinition> campaignCaptor =
                ArgumentCaptor.forClass(IndexDefinition.class);
        verify(campaignIndexOperations, times(8)).ensureIndex(campaignCaptor.capture());
        assertCampaignIndexes(campaignCaptor.getAllValues().subList(0, 4));
        assertCampaignIndexes(campaignCaptor.getAllValues().subList(4, 8));

        ArgumentCaptor<IndexDefinition> deliveryCaptor =
                ArgumentCaptor.forClass(IndexDefinition.class);
        verify(deliveryIndexOperations, times(8)).ensureIndex(deliveryCaptor.capture());
        assertDeliveryIndexes(deliveryCaptor.getAllValues().subList(0, 4));
        assertDeliveryIndexes(deliveryCaptor.getAllValues().subList(4, 8));
    }

    @Test
    void indexCreationFailureIsNotSwallowed() {
        when(mongoTemplate.indexOps(NewsletterSubscriber.class))
                .thenReturn(subscriberIndexOperations);
        doThrow(new IllegalStateException("index conflict"))
                .when(subscriberIndexOperations)
                .ensureIndex(any(IndexDefinition.class));
        NewsletterMongoIndexInitializer initializer =
                new NewsletterMongoIndexInitializer(mongoTemplate);

        assertThatThrownBy(() -> initializer.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("index conflict");
    }

    @Test
    void initializerIsDisabledForTestProfile() {
        Profile profile = NewsletterMongoIndexInitializer.class.getAnnotation(Profile.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("!test");
    }

    private void assertEmailIndex(IndexDefinition definition) {
        assertThat(definition.getIndexKeys()).isEqualTo(new Document("email", 1));
        assertThat(definition.getIndexOptions().getString("name"))
                .isEqualTo(NewsletterMongoIndexInitializer.EMAIL_INDEX_NAME);
        assertThat(definition.getIndexOptions().getBoolean("unique")).isTrue();
    }

    private void assertStatusIndex(IndexDefinition definition) {
        assertThat(definition.getIndexKeys()).isEqualTo(new Document("status", 1));
        assertThat(definition.getIndexOptions().getString("name"))
                .isEqualTo(NewsletterMongoIndexInitializer.STATUS_INDEX_NAME);
        assertThat(definition.getIndexOptions().containsKey("unique")).isFalse();
    }

    private void assertCampaignIndexes(List<IndexDefinition> definitions) {
        assertIndex(
                definitions.get(0),
                new Document("postId", 1),
                NewsletterMongoIndexInitializer.CAMPAIGN_POST_INDEX_NAME,
                true);
        assertIndex(
                definitions.get(1),
                new Document("status", 1).append("scheduledAt", 1),
                NewsletterMongoIndexInitializer.CAMPAIGN_SCHEDULE_INDEX_NAME,
                false);
        assertIndex(
                definitions.get(2),
                new Document("status", 1).append("claimExpiresAt", 1),
                NewsletterMongoIndexInitializer.CAMPAIGN_CLAIM_INDEX_NAME,
                false);
        assertIndex(
                definitions.get(3),
                new Document("status", 1).append("updatedAt", 1),
                NewsletterMongoIndexInitializer.CAMPAIGN_UPDATED_INDEX_NAME,
                false);
    }

    private void assertDeliveryIndexes(List<IndexDefinition> definitions) {
        assertIndex(
                definitions.get(0),
                new Document("postId", 1).append("subscriberId", 1),
                NewsletterMongoIndexInitializer.DELIVERY_RECIPIENT_INDEX_NAME,
                true);
        assertIndex(
                definitions.get(1),
                new Document("campaignId", 1).append("status", 1),
                NewsletterMongoIndexInitializer.DELIVERY_CAMPAIGN_INDEX_NAME,
                false);
        assertIndex(
                definitions.get(2),
                new Document("status", 1).append("nextRetryAt", 1),
                NewsletterMongoIndexInitializer.DELIVERY_RETRY_INDEX_NAME,
                false);
        assertIndex(
                definitions.get(3),
                new Document("status", 1).append("claimExpiresAt", 1),
                NewsletterMongoIndexInitializer.DELIVERY_CLAIM_INDEX_NAME,
                false);
    }

    private void assertIndex(
            IndexDefinition definition,
            Document keys,
            String name,
            boolean unique) {
        assertThat(definition.getIndexKeys()).isEqualTo(keys);
        assertThat(definition.getIndexOptions().getString("name")).isEqualTo(name);
        if (unique) {
            assertThat(definition.getIndexOptions().getBoolean("unique")).isTrue();
        } else {
            assertThat(definition.getIndexOptions().containsKey("unique")).isFalse();
        }
    }
}
